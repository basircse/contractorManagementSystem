package com.ccms.auth;

import com.ccms.audit.AuditService;
import com.ccms.common.ApiException;
import com.ccms.common.ErrorCode;
import com.ccms.platform.*;
import com.ccms.security.AuthPrincipal;
import com.ccms.security.CurrentUser;
import com.ccms.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserRepository users;
    private final ContractorRepository contractors;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record MeDto(Long id, String username, String fullName, Role role,
                        Long contractorId, String contractorName, String preferredLang) {
    }

    public record LoginResponse(String token, long expiresIn, MeDto user) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record LanguageRequest(@NotBlank @Pattern(regexp = "bn|en") String lang) {
    }

    @PostMapping("/login")
    @Transactional
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.findByUsernameIgnoreCase(req.username().trim())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_CREDENTIALS, "Invalid username or password"));

        Contractor contractor = user.getContractorId() == null ? null
                : contractors.findById(user.getContractorId()).orElse(null);
        boolean blocked = !user.isEnabled()
                || (user.getRole() == Role.CONTRACTOR && (contractor == null || contractor.getStatus() == ContractorStatus.BLOCKED));
        if (blocked) {
            throw new ApiException(ErrorCode.ACCOUNT_BLOCKED, "Account is blocked");
        }

        user.setLastLoginAt(LocalDateTime.now());
        audit.logFor(user.getContractorId(), "LOGIN", "USER", user.getId(), null);
        return new LoginResponse(jwt.issue(user), jwt.expirySeconds(), toMe(user, contractor));
    }

    @GetMapping("/me")
    public MeDto me() {
        AppUser user = currentUser();
        Contractor contractor = user.getContractorId() == null ? null
                : contractors.findById(user.getContractorId()).orElse(null);
        return toMe(user, contractor);
    }

    @PostMapping("/change-password")
    @Transactional
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        AppUser user = currentUser();
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.WRONG_PASSWORD, "Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        audit.logFor(user.getContractorId(), "PASSWORD_CHANGED", "USER", user.getId(), null);
    }

    @PutMapping("/language")
    @Transactional
    public void setLanguage(@Valid @RequestBody LanguageRequest req) {
        currentUser().setPreferredLang(req.lang());
    }

    private AppUser currentUser() {
        AuthPrincipal p = CurrentUser.get();
        return users.findById(p.userId()).orElseThrow(() -> ApiException.notFound("User", p.userId()));
    }

    private static MeDto toMe(AppUser u, Contractor c) {
        return new MeDto(u.getId(), u.getUsername(), u.getFullName(), u.getRole(),
                u.getContractorId(), c != null ? c.getName() : null, u.getPreferredLang());
    }
}
