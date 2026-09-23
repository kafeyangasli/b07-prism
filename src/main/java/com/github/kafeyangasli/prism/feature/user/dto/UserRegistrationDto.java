package com.github.kafeyangasli.prism.feature.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRegistrationDto {
    private String name;
    private String email;
    private String password;
}
