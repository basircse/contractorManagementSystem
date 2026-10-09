package com.ccms.hierarchy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "unit")
public class Unit extends HierarchyNode {

    @Column(nullable = false)
    private Long floorId;
}
