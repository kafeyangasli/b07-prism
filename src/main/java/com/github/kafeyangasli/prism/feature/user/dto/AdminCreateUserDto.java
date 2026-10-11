package com.github.kafeyangasli.prism.feature.user.dto;

import com.github.kafeyangasli.prism.feature.user.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminCreateUserDto {
    @NotBlank(message = "Nama lengkap wajib diisi.")
    private String name;

    @NotBlank(message = "Email wajib diisi.")
    @Email(message = "Format email tidak valid.")
    private String email;

    @NotBlank(message = "Kata sandi wajib diisi.")
    private String password;

    @NotNull(message = "Peran wajib dipilih.")
    private Role role;
}
