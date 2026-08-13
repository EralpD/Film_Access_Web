package com.example.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.user.Dto.RegistrationRequest;
import com.example.user.service.UserRegistrationService;

import jakarta.validation.Valid;

@Controller
public class RegistrationController {
    
    private final UserRegistrationService userRegistrationService;

    public RegistrationController(
        UserRegistrationService userRegistrationService
    ){
        this.userRegistrationService = userRegistrationService;
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model){
        model.addAttribute(
            "registrationRequest",
            new RegistrationRequest()
        );

        return "register";
    }

    @PostMapping("/register")
    public String register(
        @Valid
        @ModelAttribute("registrationRequest")
        RegistrationRequest request,
        BindingResult bindingResult
    ){
        if (!request.getPassword().equals(request.getPasswordConfirmation()))
        {
            bindingResult.rejectValue("passwordConfirmation", "password.mismatch", "Passwords do not match.");
        }
        if(bindingResult.hasErrors()){
            return "register";
        }

         userRegistrationService.register(request);

        return "redirect:/login?registered";
    }
}
