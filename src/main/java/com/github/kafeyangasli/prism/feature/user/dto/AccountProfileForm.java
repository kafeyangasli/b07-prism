package com.github.kafeyangasli.prism.feature.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class AccountProfileForm {
    @NotBlank(message = "Nama tampilan wajib diisi.")
    @Size(max = 120, message = "Nama tampilan maksimal 120 karakter.")
    private String name;

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }
}
