package com.github.kafeyangasli.prism.feature.facility.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacilityTypeDto {

    @NotBlank(message = "Kode tipe fasilitas wajib diisi.")
    @Size(max = 80, message = "Kode tipe fasilitas maksimal 80 karakter.")
    private String code;

    @NotBlank(message = "Nama tipe fasilitas wajib diisi.")
    @Size(max = 150, message = "Nama tipe fasilitas maksimal 150 karakter.")
    private String name;

    @Size(max = 2000, message = "Deskripsi tipe fasilitas maksimal 2000 karakter.")
    private String description;
}
