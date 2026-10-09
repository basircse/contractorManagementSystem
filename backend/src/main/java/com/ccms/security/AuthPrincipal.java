package com.ccms.security;

import com.ccms.platform.Role;

/** The authenticated caller. For ADMIN, {@code contractorId} is null. */
public record AuthPrincipal(Long userId, String username, Role role, Long contractorId) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
