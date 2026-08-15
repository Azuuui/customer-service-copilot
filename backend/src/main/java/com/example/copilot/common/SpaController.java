package com.example.copilot.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Keeps the browser on Spring Boot while React handles the admin route. */
@Controller
public class SpaController {
    @GetMapping("/admin")
    public String admin() {
        return "forward:/index.html";
    }
}
