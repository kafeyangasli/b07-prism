package com.github.kafeyangasli.prism.feature.user;

import com.github.kafeyangasli.prism.feature.user.controller.AccountSettingsController;
import com.github.kafeyangasli.prism.feature.user.dto.*;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.service.AccountSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountSettingsControllerTest {
    @Test void persistenceFailuresRenderSafeFeedbackAndKeepSession() {
        AccountSettingsService service = mock(AccountSettingsService.class);
        when(service.currentAccount()).thenReturn(new AccountSettingsView("Name", "a@example.test", Role.PENGGUNA, AccountStatus.ACTIVE));
        var controller = new AccountSettingsController(service);
        var failure = new DataAccessResourceFailureException("Sensitive database details");
        doThrow(failure).when(service).updateProfile(any());
        doThrow(failure).when(service).changePassword(any());
        doThrow(failure).when(service).deactivateAccount(any());
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var session = request.getSession();
        var profile = new AccountProfileForm(); profile.setName("Safe input");
        var errors = new BeanPropertyBindingResult(profile, "profileForm");
        var model = new ExtendedModelMap(); model.addAttribute("profileForm", profile);
        assertThat(controller.profile(profile, errors, model, new RedirectAttributesModelMap())).isEqualTo("account/settings");
        assertThat(errors.getAllErrors().getFirst().getDefaultMessage()).doesNotContain("Sensitive database details");
        model.clear();
        assertThat(controller.password("old-secret", "new-secret", "new-secret", model, request, response)).isEqualTo("account/password");
        assertThat(model.toString()).doesNotContain("old-secret", "new-secret", "Sensitive database details");
        model.clear();
        assertThat(controller.delete(true, "old-secret", model, request, response)).isEqualTo("account/delete");
        assertThat(model.toString()).doesNotContain("old-secret", "Sensitive database details");
        assertThat(request.getSession(false)).isSameAs(session);
    }
}
