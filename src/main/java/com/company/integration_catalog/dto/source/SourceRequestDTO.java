package com.company.integration_catalog.dto.source;

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
public class SourceRequestDTO {
    @NotBlank(message = "Source name cannot be blank")
    @Size(max = 100, message = "Source name cannot exceed 100 characters")
    private String name;

    @NotNull(message = "Source description cannot be null")
    @Size(max = 500, message = "Source description cannot exceed 500 characters")
    private String description;
}
