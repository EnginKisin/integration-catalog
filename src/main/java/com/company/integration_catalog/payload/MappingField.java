package com.company.integration_catalog.payload;

import java.util.List;

public record MappingField(String path, String type, boolean nullable, boolean array, List<Object> sampleValues) {}
