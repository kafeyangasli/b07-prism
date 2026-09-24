package com.github.kafeyangasli.prism.feature.facility.dto;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacilityDto {
    @NotBlank(message = "Kode fasilitas wajib diisi.")
    private String code;

    @NotBlank(message = "Nama fasilitas wajib diisi.")
    private String name;

    @NotBlank(message = "Tipe fasilitas wajib diisi.")
    private String type;

    @NotBlank(message = "Lokasi fasilitas wajib diisi.")
    private String location;

    @NotNull(message = "Kapasitas fasilitas wajib diisi.")
    @Positive(message = "Kapasitas fasilitas harus lebih dari nol.")
    private Integer capacity;
    private String description;
    private AdministrativeStatus administrativeStatus;
}
