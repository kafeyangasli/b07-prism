package com.github.kafeyangasli.prism.feature.facility;

import com.github.kafeyangasli.prism.feature.facility.controller.AdminFacilityTypeController;
import com.github.kafeyangasli.prism.feature.facility.dto.FacilityTypeDto;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminFacilityTypeControllerTest {

    @Test
    void adminCreateRouteDelegatesToFacilityTypeBusinessService() {
        FacilityTypeService service = mock(FacilityTypeService.class);
        AdminFacilityTypeController controller = new AdminFacilityTypeController(service);
        FacilityTypeDto dto = new FacilityTypeDto();
        dto.setCode("LAB");
        dto.setName("Laboratorium");

        String result = controller.create(dto,
                new BeanPropertyBindingResult(dto, "facilityTypeDto"),
                new org.springframework.ui.ConcurrentModel(),
                new RedirectAttributesModelMap());

        assertEquals("redirect:/admin/facility-types", result);
        verify(service).create(dto);
    }
}
