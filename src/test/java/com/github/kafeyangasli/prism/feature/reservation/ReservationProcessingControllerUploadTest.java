package com.github.kafeyangasli.prism.feature.reservation;

import com.github.kafeyangasli.prism.feature.administration.service.StaffActorResolver;
import com.github.kafeyangasli.prism.feature.reservation.controller.ReservationProcessingController;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static com.github.kafeyangasli.prism.support.ProposalPdf.upload;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationProcessingControllerUploadTest {
    @Test void databaseFailureDisplaysErrorWithoutLeakingInternalDetailsOrSuccess() throws Exception {
        var service = mock(ReservationProcessingService.class);
        var actors = mock(StaffActorResolver.class);
        when(actors.resolveId("staff@test")).thenReturn(5L);
        when(service.validateProposal(eq(42L), eq(5L), any()))
                .thenThrow(new DataIntegrityViolationException("private database details"));
        var controller = new ReservationProcessingController(service, actors);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var result = mvc.perform(multipart("/staff/reservations/42/proposal/validate").file(upload())
                        .principal(new TestingAuthenticationToken("staff@test", "n/a", "ROLE_PETUGAS")))
                .andExpect(redirectedUrl("/staff/reservations"))
                .andExpect(flash().attributeExists("error")).andExpect(flash().attributeCount(1)).andReturn();
        assertThat(result.getFlashMap().get("error").toString()).contains("gagal disimpan").doesNotContain("private database details");
    }

    @Test void servletUploadLimitDisplaysPdfSizeError() {
        var controller = new ReservationProcessingController(mock(ReservationProcessingService.class), mock(StaffActorResolver.class));
        var response = controller.oversizedProposal();
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).contains("PDF", "batas unggahan");
    }
}
