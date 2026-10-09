package Evan.Application.Fitness.Web;

import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.UsageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Records a usage event after each successful log (a POST that redirects). Pages add
 * {@code _elapsed} (milliseconds the page was open) and, for workouts, {@code _source}.
 */
@Configuration
public class UsageTracking implements WebMvcConfigurer {
    private static final Pattern RELOG = Pattern.compile("^/fuel/\\d+/relog$");
    private static final Set<String> WORKOUT_SOURCES = Set.of("live", "start", "paste", "repeat");

    private final UsageService usage;

    public UsageTracking(UsageService usage) {
        this.usage = usage;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
                if (ex != null || !"POST".equals(request.getMethod()) || response.getStatus() / 100 != 3) {
                    return;
                }
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal me)) {
                    return;
                }
                String path = request.getRequestURI();
                String[] what = switch (path) {
                    case "/train" -> new String[]{"workout", WORKOUT_SOURCES.contains(request.getParameter("_source"))
                            ? request.getParameter("_source") : "typed"};
                    case "/fuel" -> new String[]{"food", "typed"};
                    case "/fuel/copy-meal" -> new String[]{"food", "yesterday"};
                    case "/recover" -> new String[]{"feel", "form"};
                    case "/recover/water" -> new String[]{"water", "tap"};
                    case "/body" -> new String[]{"weight", "form"};
                    default -> RELOG.matcher(path).matches() ? new String[]{"food", "tap"} : null;
                };
                if (what != null) {
                    usage.record(me.getId(), what[0], what[1], seconds(request.getParameter("_elapsed")));
                }
            }
        }).addPathPatterns("/train", "/fuel", "/fuel/*/relog", "/fuel/copy-meal", "/recover", "/recover/water", "/body");
    }

    /** Page-open time in whole seconds; ignored when missing or implausible (over an hour, e.g. a tab left open). */
    static Integer seconds(String elapsedMs) {
        if (elapsedMs == null) {
            return null;
        }
        try {
            long ms = Long.parseLong(elapsedMs.trim());
            return ms < 0 || ms > 3_600_000 ? null : (int) Math.round(ms / 1000.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
