package com.company.integration_catalog.dto;

import jakarta.validation.constraints.NotNull;

public record ActiveStatusRequest(@NotNull Boolean active) {}
