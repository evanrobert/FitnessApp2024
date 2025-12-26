package Evan.Application.Fitness.Controller;



import Evan.Application.Fitness.Model.Reports;
import Evan.Application.Fitness.Model.UserLoginDetails;
import Evan.Application.Fitness.Repositorys.ReportsRepository;
import Evan.Application.Fitness.Repositorys.UserLoginDetailsRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;



import java.security.Principal;

@Controller
@RequestMapping("/hra")
public class ReportsController {

    @Autowired
    private ReportsRepository reportsRepository;

    @Autowired
    private UserLoginDetailsRepository userLoginDetailsRepository;

    @GetMapping("/upload")
    public String showUploadForm(Model model, Principal principal) {

        String username = principal.getName();
        UserLoginDetails userLoginDetails =
                userLoginDetailsRepository.findByUsername(username);

        Reports report =
                reportsRepository.findByUserLoginDetails(userLoginDetails);

        if (report == null) {
            report = new Reports();
        }

        model.addAttribute("reports", report);
        return "hra-upload";
    }


    @PostMapping("/upload")
    public String uploadHRAFile(@RequestParam("file") MultipartFile file,
                                Principal principal) {

        String username = principal.getName();
        UserLoginDetails userLoginDetails =
                userLoginDetailsRepository.findByUsername(username);

        try {
            Reports report =
                    reportsRepository.findByUserLoginDetails(userLoginDetails);

            if (report == null) {
                report = Reports.builder()
                        .userLoginDetails(userLoginDetails)
                        .build();
            }

            report.setFileName(file.getOriginalFilename());
            report.setFileType(file.getContentType());
            report.setData(file.getBytes());

            reportsRepository.save(report);
            return "home";

        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

}
