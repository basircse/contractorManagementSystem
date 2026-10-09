package com.ccms.labour;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "labour")
public class Labour extends TenantEntity {

    @Column(nullable = false)
    private String name;
    private String phone;
    private String nid;
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillTier skillTier;

    private LocalDate joinedOn;
    private boolean active = true;
}
