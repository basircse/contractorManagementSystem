package com.ccms.platform;

import com.ccms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Null for ADMIN. */
    private Long contractorId;

    private boolean enabled = true;

    @Column(nullable = false)
    private String preferredLang = "bn";

    private LocalDateTime lastLoginAt;
}
