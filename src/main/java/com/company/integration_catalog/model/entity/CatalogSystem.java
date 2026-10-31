package com.company.integration_catalog.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "systems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogSystem {
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;
}
