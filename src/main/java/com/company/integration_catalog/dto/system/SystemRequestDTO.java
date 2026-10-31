package com.company.integration_catalog.dto.system;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemRequestDTO {
    @NotBlank(message = "System name cannot be blank")
    @Size(max = 100, message = "System name cannot exceed 100 characters")
    private String name;

    @NotNull(message = "System description cannot be null")
    @Size(max = 500, message = "System description cannot exceed 500 characters")
    private String description;
}
