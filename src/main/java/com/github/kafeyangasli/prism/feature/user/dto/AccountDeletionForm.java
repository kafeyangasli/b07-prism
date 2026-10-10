package com.github.kafeyangasli.prism.feature.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountDeletionForm {
    @AssertTrue(message = "Anda harus menyetujui konfirmasi penonaktifan akun.")
    private boolean confirmed;
    @NotBlank(message = "Kata sandi saat ini wajib diisi.")
    private String currentPassword;
}
