package com.ccms.hierarchy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "floor")
public class Floor extends HierarchyNode {

    @Column(nullable = false)
    private Long buildingId;
    private Integer levelNo;
}
