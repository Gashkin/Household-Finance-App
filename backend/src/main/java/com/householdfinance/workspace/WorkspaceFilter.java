package com.householdfinance.workspace;

import com.householdfinance.access.AccessContext;
import com.householdfinance.access.AccessService;

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
    private record Session(Instant created, Instant seen, String userId, long revision, Instant verified) {}
    private AccessService users;
    private final Map<String, Session> sessions = new HashMap<>();
    private final byte[] passwordHash;
    private final byte[] salt = BackupCrypto.random(16);
    private final Set<String> origins;
    private final Clock clock;
    private final boolean configured;
    private int attempts;
    private Instant attemptWindow = Instant.EPOCH;
    private record Attempts(Instant start,int count) {}
    private final Map<String,Attempts> namedAttempts=new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    @org.springframework.beans.factory.annotation.Autowired
    public WorkspaceFilter(@Value("${workspace.password:}") String password,
            @Value("${workspace.origins:http://127.0.0.1:5173,http://localhost:5173}") String origins, AccessService users) {
        this(password, origins, Clock.systemUTC());
        this.users = users;
    }
    public WorkspaceFilter(String password, String origins) {
        this(password, origins, Clock.systemUTC());
    }
    WorkspaceFilter(String password, String origins, Clock clock) {
        this.clock = clock;
        configured = password.length() >= 16 && password.length() <= 256;
        passwordHash = configured ? BackupCrypto.derive(password, salt) : new byte[32];
        this.origins = Set.copyOf(Arrays.asList(origins.split(",")));
    }
    public boolean configured() { return users==null?configured:users.configured(); }
    public synchronized boolean authenticated(HttpServletRequest request) {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(e -> expired(e.getValue(), now));
        String token = token(request);
        Session session = sessions.get(token);
        if (session == null) return false;
        if (users != null && users.active(session.userId(),session.revision()) == null) { sessions.remove(token); return false; }
        Instant seen=Set.of("/api/workspace/status","/api/access/me").contains(request.getRequestURI())?session.seen():now;
        sessions.put(token, new Session(session.created(), seen, session.userId(), session.revision(),session.verified()));
        return true;
    }
    private boolean expired(Session s, Instant now) {
        return !now.isBefore(s.seen().plusSeconds(1800)) || !now.isBefore(s.created().plusSeconds(28800));
    }
    public synchronized String sessionBinding(HttpServletRequest request) {
        if (!authenticated(request)) throw new IllegalArgumentException("Unlock the workspace before connecting");
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token(request).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String token(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie c : request.getCookies()) if (c.getName().equals("workspace_session")) return c.getValue();
        return "";
    }
    public synchronized int login(String password, HttpServletRequest request, HttpServletResponse response) {
        return login(null,password,request,response);
    }
    public synchronized int login(String username,String password, HttpServletRequest request, HttpServletResponse response) {
        if (!configured()) return 503;
        Instant now = clock.instant();
        if (!now.isBefore(attemptWindow.plusSeconds(60))) { attempts = 0; attemptWindow = now; }
        String login=Objects.toString(username,"owner").strip().toLowerCase(Locale.ROOT);
        if(login.length()>40)return 401;
        if(users==null){if (attempts >= 5) return 429;attempts++;}
        else {
            namedAttempts.entrySet().removeIf(e -> !now.isBefore(e.getValue().start().plusSeconds(60)));
            if(namedAttempts.size()>=100&&!namedAttempts.containsKey(login))return 429;
            var a=namedAttempts.getOrDefault(login,new Attempts(now,0));
            if(a.count()>=5)return 429;
            namedAttempts.put(login,new Attempts(a.start(),a.count()+1));
        }
        AccessContext.Actor actor=users==null?null:users.authenticate(username,password);
        if (users!=null ? actor==null : password == null || password.length() > 256 || !MessageDigest.isEqual(passwordHash, BackupCrypto.derive(password, salt))) return 401;
        attempts = 0;
        namedAttempts.remove(login);
        sessions.entrySet().removeIf(e -> expired(e.getValue(), now));
        if (sessions.size() >= 32) return 429;
        sessions.remove(token(request));
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(BackupCrypto.random(32));
        sessions.put(token, new Session(now, now,actor==null?"owner":actor.id(),actor==null?0:actor.revision(),now));
        cookie(response, token, request.isSecure(), 28800);
        return 204;
    }
    public synchronized void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.remove(token(request)); cookie(response, "", request.isSecure(), 0);
    }
    public synchronized AccessContext.Actor actor(HttpServletRequest request) {
        if(!authenticated(request))return null;
        var s=sessions.get(token(request));
        return users==null?new AccessContext.Actor("owner","owner",Set.of("ADMIN","EDITOR","READER"),0,true):users.active(s.userId(),s.revision());
    }
    public synchronized boolean recent(HttpServletRequest request) {
        var s=sessions.get(token(request));return s!=null&&clock.instant().isBefore(s.verified().plusSeconds(300));
    }
    public synchronized boolean reauthenticate(HttpServletRequest request,String password) {
        var a=actor(request);if(a==null||users==null)return false;
        Instant now=clock.instant();String key="reauth:"+a.id();var count=namedAttempts.getOrDefault(key,new Attempts(now,0));
        if(!now.isBefore(count.start().plusSeconds(60)))count=new Attempts(now,0);
        if(count.count()>=5)return false;
        namedAttempts.put(key,new Attempts(count.start(),count.count()+1));
        if(!users.verifies(a.id(),password)){users.audit(a.id(),a.id(),"REAUTH","DENIED");return false;}
        namedAttempts.remove(key);users.audit(a.id(),a.id(),"REAUTH","SUCCESS");
        var s=sessions.get(token(request));sessions.put(token(request),new Session(s.created(),s.seen(),s.userId(),s.revision(),clock.instant()));return true;
    }
    public synchronized void revokeAll(){sessions.clear();}
    private void cookie(HttpServletResponse response, String token, boolean secure, int maxAge) {
        response.addHeader("Set-Cookie", "workspace_session=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAge + (secure ? "; Secure" : ""));
    }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        if (!Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())) { deny(response, 403, "Loopback connection required"); return; }
        String path = request.getRequestURI();
        if(path.contains("%")||path.contains(";")||path.contains("//")){deny(response,400,"Invalid workspace path");return;}
        // Reject DNS rebinding hosts even if a caller supplies no Origin header.
        if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(request.getServerName())) { deny(response, 403, "Local workspace host required"); return; }
        String origin = request.getHeader("Origin");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        boolean callbackNavigation = path.equals("/bank/callback") && request.getMethod().equals("GET") && "navigate".equals(request.getHeader("Sec-Fetch-Mode")) && "document".equals(request.getHeader("Sec-Fetch-Dest"));
        if ((origin != null && !allowedOrigin(origin, request)) || ("cross-site".equals(fetchSite) && !callbackNavigation)) { deny(response, 403, "Untrusted workspace origin"); return; }
        if (path.equals("/health") && request.getMethod().equals("GET")) { chain.doFilter(request,response); return; }
        if (path.startsWith("/h2-console")) { deny(response, 404, "Not found"); return; }
        boolean safe = Set.of("GET", "HEAD").contains(request.getMethod());
        if (!safe && !"1".equals(request.getHeader("X-Workspace-Request"))) { deny(response, 403, "Workspace request header required"); return; }
        boolean shell = request.getMethod().equals("GET") && (path.equals("/") || path.equals("/index.html") || path.equals("/bank/callback") || path.matches("/assets/[A-Za-z0-9_-]+\\.(?:js|css|svg)"));
        boolean publicRoute = shell || (path.equals("/api/workspace/status") && request.getMethod().equals("GET")) || (Set.of("/api/workspace/unlock","/api/access/accept","/api/access/recover").contains(path) && request.getMethod().equals("POST"));
        var guard = safe ? lock.readLock() : lock.writeLock();
        var previousActor=AccessContext.actor();
        guard.lock();
        try {
            if (!publicRoute && (!configured() || !authenticated(request))) { response.setHeader("X-Workspace-Locked", "1"); deny(response, configured() ? 401 : 503, configured() ? "Unlock the workspace" : "Configure WORKSPACE_PASSWORD (16–256 characters) and restart"); return; }
            if(!publicRoute && users!=null){
                var actor=actor(request);
                if(actor==null||!users.permits(path,request.getMethod(),actor)){deny(response,403,"Capability not granted");return;}
                boolean sensitive=!safe&&(path.startsWith("/api/access/")&&!path.equals("/api/access/reauth")||path.equals("/api/workspace/backup")||path.equals("/api/workspace/restore")||path.endsWith("/sharing")||path.endsWith("/reopen"));
                if(sensitive&&!recent(request)){deny(response,403,"Confirm your password in Household access before this sensitive change");return;}
                AccessContext.enter(actor);
            }
            chain.doFilter(request, response);
        } finally { if(previousActor==null)AccessContext.clear();else AccessContext.enter(previousActor);guard.unlock(); }
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
