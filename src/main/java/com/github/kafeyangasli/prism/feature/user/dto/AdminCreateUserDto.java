package com.github.kafeyangasli.prism.feature.user.dto;

import com.github.kafeyangasli.prism.feature.user.model.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminCreateUserDto {
    private String name;
    private String email;
    private String password;
    private Role role;
}
