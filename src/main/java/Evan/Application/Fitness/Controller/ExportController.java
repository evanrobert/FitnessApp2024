package Evan.Application.Fitness.Controller;

import Evan.Application.Fitness.Security.AppUserPrincipal;
import Evan.Application.Fitness.Service.CsvExporter;
import Evan.Application.Fitness.Service.ExportService;
import Evan.Application.Fitness.Service.TodayService;
import Evan.Application.Fitness.Web.NotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Controller
public class ExportController {
    private final ExportService exports;
    private final TodayService todayService;

    public ExportController(ExportService exports, TodayService todayService) {
        this.exports = exports;
        this.todayService = todayService;
    }

    @GetMapping("/export")
    public String page(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        new TreeMap<>(ExportService.DATASETS).keySet().forEach(k -> counts.put(k, exports.table(me.getId(), k).rows().size()));
        model.addAttribute("datasets", ExportService.DATASETS);
        model.addAttribute("counts", counts);
        return "profile/export";
    }

    @GetMapping("/export/{dataset}.csv")
    public void csv(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String dataset, HttpServletResponse response)
            throws IOException {
        ExportService.Table table = exports.table(me.getId(), dataset);
        if (table == null) {
            throw new NotFoundException("Dataset");
        }
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + dataset + "-" + todayService.today(me.getId()) + ".csv\"");
        CsvExporter.write(response.getWriter(), table.header(), table.rows());
    }
}
