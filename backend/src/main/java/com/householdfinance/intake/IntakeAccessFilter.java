package com.householdfinance.intake;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IntakeAccessFilter extends OncePerRequestFilter {
    private final String key;
    public IntakeAccessFilter(@Value("${evidence.intake.access-key:}") String key) { this.key = key; }
    public boolean enabled() { return key.length() >= 16; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/evidence-intake/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (request.getMethod().equals("GET") && request.getRequestURI().equals("/api/evidence-intake/capabilities")) { chain.doFilter(request, response); return; }
        String supplied = request.getHeader("X-Intake-Key");
        if (!enabled()) { deny(response, 503, "Evidence intake is not enabled on this instance"); return; }
        if (supplied == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            deny(response, 401, "Evidence intake access key is required"); return;
        }
        if (request.getContentLengthLong() > 32768) { deny(response, 413, "Intake request is too large"); return; }
        chain.doFilter(request, response);
    }
    private void deny(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code); response.setContentType("text/plain;charset=UTF-8"); response.getWriter().write(message);
    }
}
