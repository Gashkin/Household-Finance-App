package com.householdfinance.workspace;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** All backend routes fail closed, including legacy paths and servlet error dispatch. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkspaceFilter extends OncePerRequestFilter {
    private record Session(Instant created, Instant seen) {}
    private final Map<String, Session> sessions = new HashMap<>();
    private final byte[] passwordHash;
    private final byte[] salt = BackupCrypto.random(16);
    private final Set<String> origins;
    private final Clock clock;
    private final boolean configured;
    private int attempts;
    private Instant attemptWindow = Instant.EPOCH;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    @org.springframework.beans.factory.annotation.Autowired
    public WorkspaceFilter(@Value("${workspace.password:}") String password,
            @Value("${workspace.origins:http://127.0.0.1:5173,http://localhost:5173}") String origins) {
        this(password, origins, Clock.systemUTC());
    }
    WorkspaceFilter(String password, String origins, Clock clock) {
        this.clock = clock;
        configured = password.length() >= 16 && password.length() <= 256;
        passwordHash = configured ? BackupCrypto.derive(password, salt) : new byte[32];
        this.origins = Set.copyOf(Arrays.asList(origins.split(",")));
    }
    public boolean configured() { return configured; }
    public synchronized boolean authenticated(HttpServletRequest request) {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(e -> expired(e.getValue(), now));
        String token = token(request);
        Session session = sessions.get(token);
        if (session == null) return false;
        sessions.put(token, new Session(session.created(), now));
        return true;
    }
    private boolean expired(Session s, Instant now) {
        return !now.isBefore(s.seen().plusSeconds(1800)) || !now.isBefore(s.created().plusSeconds(28800));
    }
    private String token(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie c : request.getCookies()) if (c.getName().equals("workspace_session")) return c.getValue();
        return "";
    }
    public synchronized int login(String password, HttpServletRequest request, HttpServletResponse response) {
        if (!configured) return 503;
        Instant now = clock.instant();
        if (!now.isBefore(attemptWindow.plusSeconds(60))) { attempts = 0; attemptWindow = now; }
        if (attempts >= 5) return 429;
        attempts++;
        if (password == null || password.length() > 256 || !MessageDigest.isEqual(passwordHash, BackupCrypto.derive(password, salt))) return 401;
        attempts = 0;
        sessions.entrySet().removeIf(e -> expired(e.getValue(), now));
        if (sessions.size() >= 32) return 429;
        sessions.remove(token(request));
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(BackupCrypto.random(32));
        sessions.put(token, new Session(now, now));
        cookie(response, token, request.isSecure(), 28800);
        return 204;
    }
    public synchronized void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.remove(token(request)); cookie(response, "", request.isSecure(), 0);
    }
    private void cookie(HttpServletResponse response, String token, boolean secure, int maxAge) {
        response.addHeader("Set-Cookie", "workspace_session=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAge + (secure ? "; Secure" : ""));
    }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        if (!Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())) { deny(response, 403, "Loopback connection required"); return; }
        String path = request.getRequestURI();
        // Reject DNS rebinding hosts even if a caller supplies no Origin header.
        if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(request.getServerName())) { deny(response, 403, "Local workspace host required"); return; }
        String origin = request.getHeader("Origin");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ((origin != null && !allowedOrigin(origin, request)) || "cross-site".equals(fetchSite)) { deny(response, 403, "Untrusted workspace origin"); return; }
        if (path.equals("/health") && request.getMethod().equals("GET")) { chain.doFilter(request,response); return; }
        if (path.startsWith("/h2-console")) { deny(response, 404, "Not found"); return; }
        boolean safe = Set.of("GET", "HEAD").contains(request.getMethod());
        if (!safe && !"1".equals(request.getHeader("X-Workspace-Request"))) { deny(response, 403, "Workspace request header required"); return; }
        boolean publicRoute = (path.equals("/api/workspace/status") && request.getMethod().equals("GET")) || (path.equals("/api/workspace/unlock") && request.getMethod().equals("POST"));
        if (!publicRoute && (!configured || !authenticated(request))) { deny(response, configured ? 401 : 503, configured ? "Unlock the workspace" : "Configure WORKSPACE_PASSWORD (16–256 characters) and restart"); return; }
        var guard = safe ? lock.readLock() : lock.writeLock();
        guard.lock();
        try { chain.doFilter(request, response); } finally { guard.unlock(); }
    }
    private boolean allowedOrigin(String origin, HttpServletRequest request) {
        try {
            URI uri = URI.create(origin);
            // Only loopback origins, never wildcard, forwarded Host, or arbitrary network exposure.
            if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost())) return false;
            String own = request.getScheme() + "://" + request.getHeader("Host");
            return origin.equals(own) || origins.contains(origin);
        } catch (Exception e) { return false; }
    }
    private void deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("text/plain;charset=UTF-8"); response.getWriter().write(message);
    }
}
