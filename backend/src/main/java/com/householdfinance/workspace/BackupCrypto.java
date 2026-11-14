package com.householdfinance.workspace;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Versioned authenticated envelope. No Java deserialization, archive, or SQL execution. */
public final class BackupCrypto {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final byte[] MAGIC = "HFBACKUP1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();
    private BackupCrypto() {}
    static byte[] random(int length) { byte[] bytes = new byte[length]; RANDOM.nextBytes(bytes); return bytes; }
    static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("Required cryptography unavailable", e); }
        finally { spec.clearPassword(); }
    }
    public static void validatePassword(String password) {
        if (password == null || password.length() < 16 || password.length() > 256) throw new IllegalArgumentException("Backup passphrase must have 16–256 characters");
    }
    public static byte[] encrypt(byte[] plain, String password) {
        validatePassword(password);
        if (plain.length > MAX_BYTES - 64) throw new IllegalArgumentException("Workspace exceeds the 10 MiB backup limit");
        byte[] salt = random(16), iv = random(12), key = derive(password, salt);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(MAGIC);
            byte[] ciphertext = cipher.doFinal(plain);
            return ByteBuffer.allocate(MAGIC.length + 28 + ciphertext.length).put(MAGIC).put(salt).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Backup encryption failed", e); }
        finally { Arrays.fill(key, (byte)0); }
    }
    public static byte[] decrypt(byte[] envelope, String password) {
        validatePassword(password);
        if (envelope.length < MAGIC.length + 44 || envelope.length > MAX_BYTES) throw new IllegalArgumentException("Invalid backup size");
        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        byte[] magic = new byte[MAGIC.length], salt = new byte[16], iv = new byte[12];
        buffer.get(magic).get(salt).get(iv);
        if (!Arrays.equals(magic, MAGIC)) throw new IllegalArgumentException("Unsupported backup format");
        byte[] key = derive(password, salt), encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(MAGIC); return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException e) { throw new IllegalArgumentException("Wrong passphrase or damaged backup"); }
        finally { Arrays.fill(key, (byte)0); }
    }
}
