package com.company.integration_catalog.ai;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.UncheckedIOException;

final class AiTestProperties {
    private AiTestProperties() {}

    static AiProperties defaults() {
        MockEnvironment environment = new MockEnvironment();
        try {
            environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return Binder.get(environment).bind("catalog.ai", AiProperties.class).get();
    }
}
