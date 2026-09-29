package Evan.Application.Fitness.Web;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Confirmation / warning banners shown after a redirect. */
public final class Flash {
    private Flash() {
    }

    public static void success(RedirectAttributes attrs, String message) {
        attrs.addFlashAttribute("flashSuccess", message);
    }

    public static void error(RedirectAttributes attrs, String message) {
        attrs.addFlashAttribute("flashError", message);
    }
}
