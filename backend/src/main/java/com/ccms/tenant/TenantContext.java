package com.ccms.tenant;

/** Holds the contractor (tenant) id for the current request thread. */
public final class TenantContext {

    /** Used when no tenant is bound: matches no rows, so nothing leaks. */
    public static final Long NO_TENANT = 0L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long contractorId) {
        CURRENT.set(contractorId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant bound to current request");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
