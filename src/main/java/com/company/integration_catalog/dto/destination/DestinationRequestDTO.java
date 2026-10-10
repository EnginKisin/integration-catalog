package com.company.integration_catalog.dto.destination;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DestinationRequestDTO {

    @NotNull(message = "Destination name cannot be null")
    @Size(max = 100, message = "Destination name cannot exceed 100 characters")
    private String name;

    @NotNull(message = "Destination description cannot be null")
    @Size(max = 500, message = "Destination description cannot exceed 500 characters")
    private String description;
}

