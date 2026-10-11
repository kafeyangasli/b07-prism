package com.github.kafeyangasli.prism.feature.blockage.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBlockageTypeRequest {

    private String code;
    private String name;
    private String description;
}
