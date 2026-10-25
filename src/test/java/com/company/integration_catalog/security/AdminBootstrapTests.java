package com.company.integration_catalog.security;

import com.company.integration_catalog.model.entity.UserAccount;
import com.company.integration_catalog.model.enums.UserRole;
import com.company.integration_catalog.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTests {
    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final org.springframework.security.crypto.password.PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
    private AdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new AdminBootstrap(repository, encoder);
        ReflectionTestUtils.setField(bootstrap, "username", "");
        ReflectionTestUtils.setField(bootstrap, "password", "");
    }

    @Test
    void emptyDatabaseRequiresExplicitAdminCredentials() {
        assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void bootstrapCreatesHashedAdmin() {
        ReflectionTestUtils.setField(bootstrap, "username", "admin");
        ReflectionTestUtils.setField(bootstrap, "password", "Initial-password-123");
        bootstrap.run(null);
        var captor = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(encoder.matches("Initial-password-123", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void existingAccountsAreNeverResetOnRestart() {
        when(repository.count()).thenReturn(1L);
        bootstrap.run(null);
        verify(repository, never()).save(any());
    }
}
