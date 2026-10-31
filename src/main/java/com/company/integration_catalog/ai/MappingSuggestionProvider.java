package com.company.integration_catalog.ai;

public interface MappingSuggestionProvider {
    String name();
    default void validateConfiguration() {}
    String availabilityCode();
    AiMappingDtos.Suggestions suggest(AiMappingDtos.Input input);
}
