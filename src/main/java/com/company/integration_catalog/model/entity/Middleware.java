package com.company.integration_catalog.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "middlewares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Middleware {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;
}