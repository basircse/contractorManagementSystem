package com.ccms.security;

import com.ccms.common.ApiError;
import com.ccms.common.ErrorCode;
import com.ccms.platform.*;
import com.ccms.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Authenticates the bearer token and binds the tenant for the request.
 * The user and contractor are re-checked on every request, so blocking a contractor
 * takes effect immediately - even for tokens that were already issued.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String CONTRACTOR_HEADER = "X-Contractor-Id";

    private final JwtService jwt;
    private final AppUserRepository users;
    private final ContractorRepository contractors;
    private final ObjectMapper mapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = req.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                Optional<AppUser> user = jwt.parseUserId(header.substring(7)).flatMap(users::findById);
                if (user.isEmpty()) {
                    reject(res, ErrorCode.UNAUTHENTICATED, "Invalid or expired token");
                    return;
                }
                AppUser u = user.get();
                if (!u.isEnabled() || isContractorBlocked(u)) {
                    reject(res, ErrorCode.ACCOUNT_BLOCKED, "Account is blocked");
                    return;
                }
                if (!bindTenant(u, req, res)) {
                    return;
                }
                AuthPrincipal principal = new AuthPrincipal(u.getId(), u.getUsername(), u.getRole(), u.getContractorId());
                var auth = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isContractorBlocked(AppUser u) {
        if (u.getRole() != Role.CONTRACTOR) {
            return false;
        }
        return contractors.findById(u.getContractorId())
                .map(c -> c.getStatus() == ContractorStatus.BLOCKED)
                .orElse(true);
    }

    /** Contractors are pinned to their own tenant; admins pick one via header (read-only). */
    private boolean bindTenant(AppUser u, HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (u.getRole() == Role.CONTRACTOR) {
            TenantContext.set(u.getContractorId());
            return true;
        }
        String cid = req.getHeader(CONTRACTOR_HEADER);
        if (cid != null && !cid.isBlank()) {
            try {
                TenantContext.set(Long.valueOf(cid.trim()));
            } catch (NumberFormatException e) {
                reject(res, ErrorCode.CONTRACTOR_REQUIRED, "Invalid contractor id");
                return false;
            }
        } else if (req.getRequestURI().startsWith("/api/app/")) {
            reject(res, ErrorCode.CONTRACTOR_REQUIRED, "Select a contractor to view");
            return false;
        }
        return true;
    }

    private void reject(HttpServletResponse res, ErrorCode code, String message) throws IOException {
        res.setStatus(code.status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), new ApiError(code.name(), message, Map.of(), List.of()));
    }
}
