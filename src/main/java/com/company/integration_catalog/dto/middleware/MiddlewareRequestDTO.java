package com.company.integration_catalog.dto.middleware;

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
public class MiddlewareRequestDTO {

    @NotBlank(message = "Middleware name cannot be blank")
    @Size(max = 100, message = "Middleware name cannot exceed 100 characters")
    private String name;

    @NotNull(message = "Middleware description cannot be null")
    @Size(max = 500, message = "Middleware description cannot exceed 500 characters")
    private String description;
}

