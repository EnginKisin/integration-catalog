package com.company.integration_catalog.model.enums;

public enum PayloadFormat {
    JSON,
    XML,
    IDOC,
    CSV,
    EXCEL;

    public boolean supportsFieldMapping() {
        return this != CSV && this != EXCEL;
    }
}
