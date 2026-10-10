package com.github.kafeyangasli.prism.feature.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.github.kafeyangasli.prism.feature.administration.repository.ApplicationSettingRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:proposal-template-settings;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ProposalTemplateSettingsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationSettingRepository settings;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenSettingsAndPersistTemplateLink() throws Exception {
        mvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"proposalTemplateUrl\"")));

        mvc.perform(post("/admin/settings/proposal-template")
                        .with(csrf())
                        .param("proposalTemplateUrl", "https://docs.example.test/shared-proposal"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"));

        assertThat(settings.findById("proposal_template_url").orElseThrow().getValue())
                .isEqualTo("https://docs.example.test/shared-proposal");
    }

    @Test
    @WithMockUser(roles = "PENGGUNA")
    void reservationFormUsesPersistedTemplateLink() throws Exception {
        settings.save(new com.github.kafeyangasli.prism.feature.administration.model.ApplicationSetting(
                "proposal_template_url", "https://docs.example.test/shared-proposal"));

        mvc.perform(get("/reservations/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"https://docs.example.test/shared-proposal\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Buka templat")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/reservations/proposal-template\""))));
    }

    @Test
    @WithMockUser(roles = "PENGGUNA")
    void userCannotChangeTemplateLink() throws Exception {
        mvc.perform(post("/admin/settings/proposal-template")
                        .with(csrf())
                        .param("proposalTemplateUrl", "https://docs.example.test/unauthorized"))
                .andExpect(status().isForbidden());

        assertThat(settings.findById("proposal_template_url")).isEmpty();
    }
}
