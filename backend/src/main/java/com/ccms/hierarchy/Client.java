package com.ccms.hierarchy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "client")
public class Client extends HierarchyNode {

    private String phone;
    private String email;
    private String address;
}
