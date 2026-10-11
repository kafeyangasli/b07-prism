package com.github.kafeyangasli.prism.feature.administration.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.kafeyangasli.prism.feature.administration.model.ApplicationSetting;
import com.github.kafeyangasli.prism.feature.administration.repository.ApplicationSettingRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;

@Service
public class ProposalTemplateSettingService {
    static final String SETTING_KEY = "proposal_template_url";
    private static final int MAX_URL_LENGTH = 2000;

    private final ApplicationSettingRepository settings;

    public ProposalTemplateSettingService(ApplicationSettingRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Optional<String> getUrl() {
        return settings.findById(SETTING_KEY).map(ApplicationSetting::getValue);
    }

    @Transactional
    public String updateUrl(String candidate) {
        String url = validate(candidate);
        ApplicationSetting setting = settings.findById(SETTING_KEY)
                .orElseGet(() -> new ApplicationSetting(SETTING_KEY, url));
        setting.updateValue(url);
        settings.save(setting);
        return url;
    }

    private String validate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new BusinessRuleException("Tautan templat proposal wajib diisi.");
        }
        String url = candidate.trim();
        if (url.length() > MAX_URL_LENGTH) {
            throw new BusinessRuleException("Tautan templat proposal maksimal 2000 karakter.");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https"))
                    || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw invalidUrl();
            }
        } catch (URISyntaxException exception) {
            throw invalidUrl();
        }
        return url;
    }

    private BusinessRuleException invalidUrl() {
        return new BusinessRuleException("Tautan templat proposal harus berupa URL HTTP atau HTTPS yang valid.");
    }
}
