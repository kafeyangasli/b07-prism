package com.github.kafeyangasli.prism.feature.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountPasswordForm {
    @NotBlank(message = "Kata sandi saat ini wajib diisi.")
    private String currentPassword;
    @NotBlank(message = "Kata sandi baru wajib diisi.")
    @Size(min = 8, max = 72, message = "Kata sandi baru harus terdiri dari 8–72 karakter (maksimal 72 byte UTF-8).")
    private String newPassword;
    @NotBlank(message = "Konfirmasi kata sandi baru wajib diisi.")
    private String confirmPassword;
}
