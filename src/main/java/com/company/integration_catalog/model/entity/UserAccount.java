package com.company.integration_catalog.model.entity;

import com.company.integration_catalog.model.enums.UserRole;
import com.company.integration_catalog.model.enums.UserPermission;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_accounts")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(unique = true, length = 254)
    private String email;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean emailVerified;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean passwordResetRequired;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long sessionVersion;

    @Version
    @Column(columnDefinition = "bigint default 0")
    private Long version;

    public boolean loginAllowed() {
        // Existing/bootstrap accounts without an email can link and verify one after login.
        return active && !passwordResetRequired && (email == null || emailVerified);
    }

    public void revokeSessions() { sessionVersion++; }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission"}))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private Set<UserPermission> permissions = new HashSet<>();
}
