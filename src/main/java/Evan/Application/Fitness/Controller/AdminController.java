package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.UsageService;
import Evan.Application.Fitness.Web.Admins;
import Evan.Application.Fitness.Web.ChartJson;
import Evan.Application.Fitness.Web.NotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The app owner's usage report: totals only, for usernames listed in ADMIN_USERNAMES. */
@Controller
public class AdminController {
    private final UsageService usage;
    private final Admins admins;
    private final ChartJson charts;

    public AdminController(UsageService usage, Admins admins, ChartJson charts) {
        this.usage = usage;
        this.admins = admins;
        this.charts = charts;
    }

    @GetMapping("/admin/usage")
    public String usage(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "30") int days, Model model) {
        if (!admins.isAdmin(me.getUsername())) {
            throw new NotFoundException("Page");
        }
        int range = days == 7 || days == 90 ? days : 30;
        UsageService.Report report = usage.report(range);
        model.addAttribute("r", report);
        model.addAttribute("days", range);
        model.addAttribute("keepDays", UsageService.KEEP_DAYS);
        model.addAttribute("membersChart", charts.write(ChartJson.spec("bar", "labels", report.dayLabels(),
                "values", report.membersPerDay(), "unit", "", "name", "Members who logged", "decimals", 0,
                "title", "Members who logged something, per day", "empty", "No logs in this range yet.")));
        return "admin/usage";
    }
}
