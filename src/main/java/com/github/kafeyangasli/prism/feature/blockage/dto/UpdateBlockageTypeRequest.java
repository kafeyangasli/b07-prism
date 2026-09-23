package com.github.kafeyangasli.prism.feature.blockage.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBlockageTypeRequest {

    private String name;
    private String description;
    private Boolean active;
}
