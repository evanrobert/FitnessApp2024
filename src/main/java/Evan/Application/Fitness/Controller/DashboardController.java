package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;
import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.AnalyticsService;
import Evan.Application.Fitness.Service.ProfileService;
import Evan.Application.Fitness.Web.ChartJson;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class DashboardController {
    private final AnalyticsService analytics;
    private final ProfileService profiles;
    private final ChartJson charts;
    private final UserLoginDetailsRepository users;

    public DashboardController(AnalyticsService analytics, ProfileService profiles, ChartJson charts,
                               UserLoginDetailsRepository users) {
        this.users = users;
        this.analytics = analytics;
        this.profiles = profiles;
        this.charts = charts;
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        AnalyticsService.Dashboard dash = analytics.dashboard(me.getId());
        model.addAttribute("d", dash);
        model.addAttribute("onboarded", profiles.onboarded(me.getId()));
        model.addAttribute("account", users.findById(me.getId()).orElseThrow());
        model.addAttribute("calendarChart", charts.write(dash.calendar()));
        model.addAttribute("weightSpark", dash.weightSpark() == null ? null : charts.write(dash.weightSpark()));
        return "home";
    }

    @GetMapping("/insights")
    public String insights(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "90") int days, Model model) {
        int range = days <= 30 ? 30 : days <= 90 ? 90 : 180;
        AnalyticsService.Insights view = analytics.insights(me.getId(), range);
        model.addAttribute("v", view);
        for (Map.Entry<String, Map<String, Object>> chart : view.charts().entrySet()) {
            model.addAttribute("chart_" + chart.getKey(), charts.write(chart.getValue()));
        }
        return "insights";
    }

    @GetMapping("/timeline")
    public String timeline(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "30") int days,
                           @RequestParam(required = false) AnalyticsService.EventType type, Model model) {
        int range = Math.max(7, Math.min(365, days));
        model.addAttribute("days", range);
        model.addAttribute("type", type);
        model.addAttribute("types", AnalyticsService.EventType.values());
        model.addAttribute("timeline", analytics.timeline(me.getId(), range, type));
        return "timeline";
    }
}
