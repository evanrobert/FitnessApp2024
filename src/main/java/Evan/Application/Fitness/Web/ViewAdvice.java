package Evan.Application.Fitness.Web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Values every page's layout needs. */
@ControllerAdvice
public class ViewAdvice {
    private final String brandName;
    private final String brandInitials;

    public ViewAdvice(@Value("${app.brand-name}") String brandName) {
        this.brandName = brandName;
        this.brandInitials = Arrays.stream(brandName.trim().split("\\s+"))
                .filter(word -> !word.isEmpty())
                .limit(2)
                .map(word -> word.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());
    }

    @ModelAttribute("brandName")
    public String brandName() {
        return brandName;
    }

    @ModelAttribute("brandInitials")
    public String brandInitials() {
        return brandInitials;
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null ? "" : "?" + query);
    }

    /** Only same-site relative paths are accepted as redirect targets. */
    public static String safeReturn(String returnTo, String fallback) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//") || returnTo.contains("\\")) {
            return fallback;
        }
        return returnTo;
    }
}
