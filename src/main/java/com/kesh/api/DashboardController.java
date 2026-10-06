package com.kesh.api;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.kesh.workspace.WorkspaceMode;

@Controller
public class DashboardController {

    @GetMapping("/")
    public String home() {
        return "redirect:/work";
    }

    @GetMapping("/professional")
    public String professional() {
        return "redirect:/work";
    }

    @GetMapping("/{desk:work|personal}")
    public String desk(@PathVariable String desk, Model model) {
        WorkspaceMode mode = WorkspaceMode.fromPath(desk);
        model.addAttribute("desk", mode.path());
        model.addAttribute("deskLabel", mode.label());
        return "index";
    }
}
