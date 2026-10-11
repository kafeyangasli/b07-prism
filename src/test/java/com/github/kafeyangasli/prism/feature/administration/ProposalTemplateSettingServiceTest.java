package com.github.kafeyangasli.prism.feature.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.kafeyangasli.prism.feature.administration.model.ApplicationSetting;
import com.github.kafeyangasli.prism.feature.administration.repository.ApplicationSettingRepository;
import com.github.kafeyangasli.prism.feature.administration.service.ProposalTemplateSettingService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;

class ProposalTemplateSettingServiceTest {
    private ApplicationSettingRepository repository;
    private ProposalTemplateSettingService service;

    @BeforeEach
    void setUp() {
        repository = mock(ApplicationSettingRepository.class);
        service = new ProposalTemplateSettingService(repository);
    }

    @Test
    void storesTrimmedHttpLink() {
        when(repository.findById("proposal_template_url")).thenReturn(Optional.empty());

        String result = service.updateUrl("  https://docs.example.test/proposal  ");

        assertThat(result).isEqualTo("https://docs.example.test/proposal");
        verify(repository).save(any(ApplicationSetting.class));
    }

    @Test
    void updatesExistingSetting() {
        ApplicationSetting existing = new ApplicationSetting("proposal_template_url", "https://old.example.test");
        when(repository.findById("proposal_template_url")).thenReturn(Optional.of(existing));

        service.updateUrl("http://new.example.test/template.docx");

        assertThat(existing.getValue()).isEqualTo("http://new.example.test/template.docx");
        verify(repository).save(existing);
    }

    @Test
    void rejectsBlankNonHttpAndCredentialBearingLinks() {
        assertThatThrownBy(() -> service.updateUrl(" "))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("wajib");
        assertThatThrownBy(() -> service.updateUrl("javascript:alert(1)"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("HTTP");
        assertThatThrownBy(() -> service.updateUrl("https://user:password@example.test/template"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("HTTP");
    }
}
