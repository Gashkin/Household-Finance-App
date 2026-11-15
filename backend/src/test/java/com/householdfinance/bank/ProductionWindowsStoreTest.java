package com.householdfinance.bank;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
@EnabledOnOs(OS.WINDOWS)
class ProductionWindowsStoreTest {
  @TempDir Path folder;
  @Test void nativeWindowsAclAndEncryptedStoreRoundtripRejectBroadAccess() throws Exception {
    var acl=Files.getFileAttributeView(folder,AclFileAttributeView.class);
    var owner=acl.getOwner();
    var privateEntry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
      .setPermissions(EnumSet.allOf(AclEntryPermission.class)).setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT).build();
    acl.setAcl(List.of(privateEntry));
    byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
    var config=new ProductionConfig(true,"11111111-1111-4111-8111-111111111111","","http://127.0.0.1:8080/bank/callback",folder.resolve("connection.bin").toString(),Base64.getEncoder().encodeToString(key),true);
    var store=new ConnectionStore(config);
    store.write(Map.of("reference","synthetic-native-session"));
    assertEquals("synthetic-native-session",new ConnectionStore(config).read(Map.class).get("reference"));
    var everyone=folder.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone");
    var broad=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone).setPermissions(AclEntryPermission.READ_DATA).build();
    acl.setAcl(List.of(privateEntry,broad));
    assertThrows(IllegalArgumentException.class,()->new ConnectionStore(config));
  }
  @Test void junctionAtTheStoredFileIsRejectedBeforeReadWriteOrReinitialization() throws Exception {
    var acl=Files.getFileAttributeView(folder,AclFileAttributeView.class);
    var entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
      .setPermissions(EnumSet.allOf(AclEntryPermission.class)).setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT).build();
    acl.setAcl(List.of(entry));
    Path path=folder.resolve("connection.bin"), outside=Files.createTempDirectory("synthetic-junction-target-");
    byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
    var config=new ProductionConfig(true,"11111111-1111-4111-8111-111111111111","","http://127.0.0.1:8080/bank/callback",path.toString(),Base64.getEncoder().encodeToString(key),true);
    var store=new ConnectionStore(config);store.write(Map.of("reference","synthetic"));Files.delete(path);
    try {
      var process=new ProcessBuilder("cmd.exe","/c","mklink","/J",path.toString(),outside.toString()).redirectErrorStream(true).start();
      assertEquals(0,process.waitFor(),new String(process.getInputStream().readAllBytes()));
      assertFalse(Files.isSymbolicLink(path));
      assertTrue(Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther());
      assertThrows(IllegalArgumentException.class,()->store.read(Map.class));
      assertThrows(IllegalArgumentException.class,()->store.write(Map.of("reference","changed")));
      assertThrows(IllegalArgumentException.class,()->new ConnectionStore(config));
      try(var contents=Files.list(outside)){assertEquals(0,contents.count());}
    } finally {Files.deleteIfExists(path);Files.deleteIfExists(outside);}
  }

}
