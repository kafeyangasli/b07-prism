package com.github.kafeyangasli.prism.feature.facility.dto;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacilityDto {
    private String code;
    private String name;
    private String type;
    private String location;
    private Integer capacity;
    private String description;
    private AdministrativeStatus administrativeStatus;
}
