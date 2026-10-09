package com.ccms.hierarchy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "building")
public class Building extends HierarchyNode {

    @Column(nullable = false)
    private Long siteId;
}
