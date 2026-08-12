package com.example.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.example.user.Dto.RegistrationRequest;
import com.example.user.service.UserRegistrationService;

class RegistrationControllerTest {

    private MockMvc mockMvc;
    private UserRegistrationService registrationService;

@BeforeEach
void setUp() {

    registrationService =
            mock(UserRegistrationService.class);

    RegistrationController controller =
            new RegistrationController(
                    registrationService
            );

    LocalValidatorFactoryBean validator =
            new LocalValidatorFactoryBean();

    validator.afterPropertiesSet();

    InternalResourceViewResolver viewResolver =
            new InternalResourceViewResolver();

    viewResolver.setPrefix("/WEB-INF/views/");
    viewResolver.setSuffix(".html");

    mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setValidator(validator)
            .setViewResolvers(viewResolver)
            .build();
}


    @Test
    void shouldShowRegistrationPage()
            throws Exception {

        mockMvc.perform(
                get("/register")
        )
        .andExpect(status().isOk())
        .andExpect(view().name("register"))
        .andExpect(
                model().attributeExists(
                        "registrationRequest"
                )
        );
    }

   @Test
    void shouldRegisterValidRequest() throws Exception {

        mockMvc.perform(
                post("/register")
                        .param("displayName", "Eralp")
                        .param("email", "eralp@example.com")
                        .param("password", "StrongPassword123!")
                        .param(
                                "passwordConfirmation",
                                "StrongPassword123!"
                        )
        )
        .andDo(print())
        .andExpect(
                status().is3xxRedirection()
        )
        .andExpect(
                redirectedUrl("/login?registered")
        );

        verify(registrationService)
                .register(
                        any(RegistrationRequest.class)
                );
    }


    @Test
    void shouldNotCallServiceWhenRequestIsInvalid()
            throws Exception {

        mockMvc.perform(
                post("/register")
                        .param(
                                "displayName",
                                ""
                        )
                        .param(
                                "email",
                                "invalid-email"
                        )
                        .param(
                                "password",
                                "short"
                        )
                        .param(
                                "passwordConfirmation",
                                ""
                        )
        )
        .andExpect(status().isOk())
        .andExpect(
                view().name("register")
        )
        .andExpect(
                model().attributeHasErrors(
                        "registrationRequest"
                )
        );

        verify(
                registrationService,
                never()
        ).register(any());
    }
}
