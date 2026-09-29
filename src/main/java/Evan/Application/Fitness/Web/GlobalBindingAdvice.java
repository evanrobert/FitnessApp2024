package Evan.Application.Fitness.Web;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Defense in depth against mass assignment: request parameters can never set a
 * record's id, owner or roles. Ids come from the URL and ownership from the
 * authenticated principal; services copy only editable fields.
 */
@ControllerAdvice
public class GlobalBindingAdvice {
    @InitBinder
    public void protectSystemFields(WebDataBinder binder) {
        binder.setDisallowedFields("id", "user", "user.*", "*.user", "*.user.*",
                "userLoginDetails*", "*.userLoginDetails*", "roles*", "*.roles*");
    }
}
