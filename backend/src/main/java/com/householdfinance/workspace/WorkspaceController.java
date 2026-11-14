package com.householdfinance.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceFilter access;
    private final WorkspaceBackupService backups;
    private final ObjectMapper json;
    public WorkspaceController(WorkspaceFilter access, WorkspaceBackupService backups, ObjectMapper json) {
        this.access=access; this.backups=backups; this.json=json;
    }
    record Password(String password) {}
    record Restore(String password, String backup, String confirmation) {}
    @GetMapping("/status") public Map<String, Object> status(HttpServletRequest request) {
        return Map.of("configured",access.configured(),"unlocked",access.authenticated(request));
    }
    @PostMapping("/unlock") public ResponseEntity<Void> unlock(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Password input = read(request,2048,Password.class);
        return ResponseEntity.status(access.login(input.password(), request,response)).build();
    }
    @PostMapping("/lock") public void lock(HttpServletRequest request,HttpServletResponse response) { access.logout(request,response); }
    @GetMapping("/records") public Map<String,Long> records() { return backups.counts(); }
    @PostMapping(value="/backup", produces=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> backup(HttpServletRequest request) throws IOException {
        Password input = read(request,2048,Password.class);
        return ResponseEntity.ok().header("Content-Disposition","attachment; filename=household-workspace.hfb").body(backups.exportBackup(input.password()));
    }
    @PostMapping("/restore") public Map<String,Long> restore(HttpServletRequest request) throws IOException {
        Restore input = read(request,14*1024*1024,Restore.class);
        if (input.backup()==null || input.backup().length()>14*1024*1024) throw new IllegalArgumentException("Backup file required (maximum 10 MiB)");
        byte[] envelope;
        try { envelope=Base64.getDecoder().decode(input.backup()); } catch (Exception e) { throw new IllegalArgumentException("Invalid backup encoding"); }
        return backups.restore(envelope,input.password(),input.confirmation());
    }
    private <T> T read(HttpServletRequest request,int max,Class<T> type) throws IOException {
        if (request.getContentLengthLong()>max) throw new IllegalArgumentException("Workspace request too large");
        byte[] bytes=request.getInputStream().readNBytes(max+1);
        if (bytes.length>max) throw new IllegalArgumentException("Workspace request too large");
        try { T value=json.readValue(bytes,type); if (value==null) throw new IllegalArgumentException("Missing request"); return value; }
        catch (IOException e) { throw new IllegalArgumentException("Invalid workspace request"); }
        finally { Arrays.fill(bytes,(byte)0); }
    }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.dao.DataAccessException.class})
    public ResponseEntity<Map<String,String>> invalid(Exception e) {
        String message = e instanceof IllegalArgumentException ? e.getMessage() : "Backup cannot be restored; no records were changed";
        return ResponseEntity.badRequest().body(Map.of("error",message==null?"Invalid workspace request":message));
    }
}
