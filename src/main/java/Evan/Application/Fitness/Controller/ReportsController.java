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

        // FIX: this is a LIST
        var reports =
                reportsRepository.findAllByUserLoginDetails(userLoginDetails);

        model.addAttribute("reports", reports);
        return "hra-upload";
    }


    @PostMapping("/upload")
    public String uploadMultipleFiles(@RequestParam("files") MultipartFile[] files,
                                      Principal principal) {

        String username = principal.getName();
        UserLoginDetails userLoginDetails =
                userLoginDetailsRepository.findByUsername(username);

        try {
            for (MultipartFile file : files) {
                Reports report = Reports.builder()
                        .fileName(file.getOriginalFilename())
                        .fileType(file.getContentType())
                        .data(file.getBytes())
                        .userLoginDetails(userLoginDetails)
                        .build();

                reportsRepository.save(report);
            }
            return "home";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }


}
