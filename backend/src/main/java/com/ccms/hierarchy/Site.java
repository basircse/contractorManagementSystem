package com.ccms.hierarchy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "site")
public class Site extends HierarchyNode {

    @Column(nullable = false)
    private Long clientId;
    private String address;
    private LocalDate startDate;
}
