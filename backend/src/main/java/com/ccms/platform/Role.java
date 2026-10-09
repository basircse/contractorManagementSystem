package com.ccms.platform;

public enum Role {
    /** Platform operator: manages contractors, read-only view of their data. */
    ADMIN,
    /** Contractor business owner: full access to own tenant data. */
    CONTRACTOR
}
