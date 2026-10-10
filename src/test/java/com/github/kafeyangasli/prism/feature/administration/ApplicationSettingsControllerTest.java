package com.github.kafeyangasli.prism.feature.administration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.github.kafeyangasli.prism.feature.administration.controller.ApplicationSettingsController;
import com.github.kafeyangasli.prism.feature.administration.service.ProposalTemplateSettingService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;

class ApplicationSettingsControllerTest {
    private ProposalTemplateSettingService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProposalTemplateSettingService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ApplicationSettingsController(service)).build();
    }

    @Test
    void settingsPageShowsCurrentLink() throws Exception {
        when(service.getUrl()).thenReturn(Optional.of("https://docs.example.test/template"));

        mvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"))
                .andExpect(model().attribute("proposalTemplateUrl", "https://docs.example.test/template"));
    }

    @Test
    void adminCanUpdateLink() throws Exception {
        mvc.perform(post("/admin/settings/proposal-template")
                        .param("proposalTemplateUrl", "https://docs.example.test/new-template"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attribute("successMessage", "Tautan templat proposal berhasil diperbarui."));

        verify(service).updateUrl("https://docs.example.test/new-template");
    }

    @Test
    void invalidLinkIsReturnedWithAnError() throws Exception {
        when(service.updateUrl("javascript:alert(1)"))
                .thenThrow(new BusinessRuleException("Tautan tidak valid."));

        mvc.perform(post("/admin/settings/proposal-template")
                        .param("proposalTemplateUrl", "javascript:alert(1)"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attribute("errorMessage", "Tautan tidak valid."))
                .andExpect(flash().attribute("proposalTemplateUrl", "javascript:alert(1)"));
    }
}
