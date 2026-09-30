package Evan.Application.Fitness.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP limits on the public forms (sign-in, sign-up, password reset), so one
 * client can't guess passwords across many accounts or flood inboxes. Works
 * alongside the per-account lockout. In-memory: limits are per app instance.
 *
 * Behind a reverse proxy, set server.forward-headers-strategy so the client IP
 * (not the proxy's) is used.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    public record Rule(String path, int max, Duration window) {
    }

    private final Map<String, Rule> rules;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private long lastSweep;

    public RateLimitFilter(Clock clock, Rule... rules) {
        this.clock = clock;
        this.rules = new ConcurrentHashMap<>();
        for (Rule rule : rules) {
            this.rules.put(rule.path(), rule);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !rules.containsKey(path(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = rules.get(path(request));
        long now = clock.millis();
        sweep(now);
        Deque<Long> times = hits.computeIfAbsent(rule.path() + "|" + request.getRemoteAddr(), k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (times) {
            long cutoff = now - rule.window().toMillis();
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.pollFirst();
            }
            allowed = times.size() < rule.max();
            if (allowed) {
                times.addLast(now);
            }
        }
        if (!allowed) {
            response.setHeader("Retry-After", String.valueOf(rule.window().toSeconds()));
            response.sendError(429, "Too many attempts");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** Drops idle entries now and then so memory stays bounded. */
    private void sweep(long now) {
        if (now - lastSweep < 60_000) {
            return;
        }
        lastSweep = now;
        long longest = rules.values().stream().mapToLong(r -> r.window().toMillis()).max().orElse(0);
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || last < now - longest;
            }
        });
    }
}
