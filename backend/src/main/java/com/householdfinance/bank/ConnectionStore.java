package com.householdfinance.bank;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** AES-GCM connection references. Key is supplied by the Windows DPAPI launcher, never backed up. */
final class ConnectionStore {
  private final Path path;
  private final byte[] key;
  private final Path marker;
  private static final String INITIALIZED = "HouseholdFinance-connection-v1";
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final byte[] context;
  ConnectionStore(ProductionConfig config) {
    if (!config.enabled()) { path = null; marker = null; key = null; context = null; return; }
    try {
      if (!config.readiness()) throw new IllegalArgumentException();
      key = Base64.getDecoder().decode(config.storageKey());
      if (key.length != 32 || config.storePath().isBlank()) throw new IllegalArgumentException();
      path = Path.of(config.storePath()).toAbsolutePath().normalize();
      marker = path.resolveSibling(path.getFileName()+".initialized");
      checkPrivate(path.getParent());
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) checkPrivate(path);
      context = ("HouseholdFinance/GBD-14/v1/" + config.appId()).getBytes(StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalArgumentException("Production requires passed local readiness and private connection storage");
    }
  }
  static void checkPrivate(Path file) throws Exception {
    if (file == null || !Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException();
    for (Path p = file; p != null; p = p.getParent()) {
      var attributes = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      // OpenJDK Windows marks non-symbolic NTFS reparse points (including junctions) as other.
      if (attributes.isSymbolicLink() || attributes.isOther() || Files.exists(p.resolve(".git"))) throw new IllegalArgumentException();
    }
    var posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
    if (posix != null) {
      var permitted = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
      if (!permitted.containsAll(posix.readAttributes().permissions())) throw new IllegalArgumentException();
    } else {
      var acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
      if (acl == null || !SandboxGateway.privateAcl(acl.getOwner().getName(), acl.getAcl())) throw new IllegalArgumentException();
    }
  }
  <T> T read(Class<T> type) {
    if (path == null) return null;
    byte[] plain = null;
    try {
      checkPrivate(path.getParent());
      verifyMarker();
      if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException();
        return null; // Explicit first provisioning: caller immediately persists the empty state.
      }
      checkPrivate(path);
      if (Files.size(path) > 65536) throw new IllegalArgumentException();
      byte[] data = Files.readAllBytes(path);
      if (data.length < 29 || data[0] != 1) throw new IllegalArgumentException();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128, Arrays.copyOfRange(data,1,13)));
      cipher.updateAAD(context);
      plain = cipher.doFinal(Arrays.copyOfRange(data,13,data.length));
      return json.readValue(plain, type);
    } catch (Exception e) {
      throw new IllegalArgumentException("Protected bank connection cannot be read; stop and recover local setup without overwriting it");
    } finally { if (plain != null) Arrays.fill(plain, (byte)0); }
  }
  private void verifyMarker() throws Exception {
    if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
      checkPrivate(marker);
      if (Files.size(marker) != INITIALIZED.length() || !Files.readString(marker,StandardCharsets.US_ASCII).equals(INITIALIZED)) throw new IllegalArgumentException();
    }
  }
  void write(Object value) {
    if (path == null) return;
    Path temp = null; byte[] plain = null;
    try {
      checkPrivate(path.getParent());
      verifyMarker();
      if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS) && !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException();
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) checkPrivate(path);
      plain = json.writeValueAsBytes(value);
      if (plain.length > 60000) throw new IllegalArgumentException();
      byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce)); cipher.updateAAD(context);
      byte[] encrypted = cipher.doFinal(plain);
      temp = Files.createTempFile(path.getParent(),"connection-", ".tmp");
      checkPrivate(temp);
      try(var channel = java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)) {
        ByteBuffer buffer=ByteBuffer.wrap(ByteBuffer.allocate(13+encrypted.length).put((byte)1).put(nonce).put(encrypted).array());
        while(buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
      }
      Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.getFileAttributeView(path.getParent(),PosixFileAttributeView.class) != null)
          Files.createFile(marker,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        else Files.createFile(marker);
        checkPrivate(marker);
        try(var channel=java.nio.channels.FileChannel.open(marker,StandardOpenOption.WRITE)) {
          var bytes=ByteBuffer.wrap(INITIALIZED.getBytes(StandardCharsets.US_ASCII));
          while(bytes.hasRemaining()) channel.write(bytes);
          channel.force(true);
        }
      }
    } catch(Exception e) {
      throw new IllegalArgumentException("Protected bank connection could not be saved; no further refresh allowed until local storage is repaired");
    } finally {
      if(plain != null) Arrays.fill(plain,(byte)0);
      if(temp != null) try { Files.deleteIfExists(temp); } catch(Exception ignored) {}
    }
  }
}
