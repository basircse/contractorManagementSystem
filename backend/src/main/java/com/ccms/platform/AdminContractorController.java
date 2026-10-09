package com.ccms.platform;

import com.ccms.audit.AuditService;
import com.ccms.catalog.DefaultCatalog;
import com.ccms.common.ApiException;
import com.ccms.common.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Platform admin: onboard, edit, block/unblock contractors. */
@RestController
@RequestMapping("/api/admin/contractors")
@RequiredArgsConstructor
public class AdminContractorController {

    private final ContractorRepository contractors;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final DefaultCatalog catalog;
    private final AuditService audit;

    public record ContractorDto(Long id, String name, String ownerName, String phone, String email, String address,
                                ContractorStatus status, String plan, String blockedReason, String username,
                                LocalDateTime lastLoginAt, LocalDateTime createdAt) {
    }

    public record CreateRequest(@NotBlank @Size(max = 150) String name,
                                @Size(max = 150) String ownerName,
                                @Size(max = 30) String phone,
                                @Email @Size(max = 150) String email,
                                @Size(max = 500) String address,
                                @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
                                @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record UpdateRequest(@NotBlank @Size(max = 150) String name,
                                @Size(max = 150) String ownerName,
                                @Size(max = 30) String phone,
                                @Email @Size(max = 150) String email,
                                @Size(max = 500) String address) {
    }

    public record BlockRequest(@Size(max = 500) String reason) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 100) String password) {
    }

    @GetMapping
    public List<ContractorDto> list() {
        Map<Long, AppUser> owners = users.findAll().stream()
                .filter(u -> u.getRole() == Role.CONTRACTOR)
                .collect(Collectors.toMap(AppUser::getContractorId, Function.identity(), (a, b) -> a));
        return contractors.findAllByOrderByNameAsc().stream()
                .map(c -> toDto(c, owners.get(c.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public ContractorDto get(@PathVariable Long id) {
        Contractor c = find(id);
        return toDto(c, owner(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ContractorDto create(@Valid @RequestBody CreateRequest req) {
        if (users.existsByUsernameIgnoreCase(req.username())) {
            throw new ApiException(ErrorCode.DUPLICATE, "Username already taken", Map.of("field", "username"));
        }
        Contractor c = new Contractor();
        apply(c, req.name(), req.ownerName(), req.phone(), req.email(), req.address());
        contractors.saveAndFlush(c);

        AppUser u = new AppUser();
        u.setUsername(req.username().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFullName(req.ownerName() != null ? req.ownerName() : req.name());
        u.setRole(Role.CONTRACTOR);
        u.setContractorId(c.getId());
        users.save(u);

        catalog.seed(c.getId());
        audit.logFor(c.getId(), "CONTRACTOR_CREATED", "CONTRACTOR", c.getId(), c.getName());
        return toDto(c, u);
    }

    @PutMapping("/{id}")
    @Transactional
    public ContractorDto update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        Contractor c = find(id);
        apply(c, req.name(), req.ownerName(), req.phone(), req.email(), req.address());
        audit.logFor(id, "CONTRACTOR_UPDATED", "CONTRACTOR", id, null);
        return toDto(c, owner(id));
    }

    @PostMapping("/{id}/block")
    @Transactional
    public ContractorDto block(@PathVariable Long id, @Valid @RequestBody(required = false) BlockRequest req) {
        Contractor c = find(id);
        c.setStatus(ContractorStatus.BLOCKED);
        c.setBlockedReason(req != null ? req.reason() : null);
        audit.logFor(id, "CONTRACTOR_BLOCKED", "CONTRACTOR", id, c.getBlockedReason());
        return toDto(c, owner(id));
    }

    @PostMapping("/{id}/unblock")
    @Transactional
    public ContractorDto unblock(@PathVariable Long id) {
        Contractor c = find(id);
        c.setStatus(ContractorStatus.ACTIVE);
        c.setBlockedReason(null);
        audit.logFor(id, "CONTRACTOR_UNBLOCKED", "CONTRACTOR", id, null);
        return toDto(c, owner(id));
    }

    @PostMapping("/{id}/reset-password")
    @Transactional
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        find(id);
        AppUser u = owner(id);
        if (u == null) {
            throw ApiException.notFound("User", id);
        }
        u.setPasswordHash(encoder.encode(req.password()));
        audit.logFor(id, "PASSWORD_RESET_BY_ADMIN", "USER", u.getId(), null);
    }

    private Contractor find(Long id) {
        return contractors.findById(id).orElseThrow(() -> ApiException.notFound("Contractor", id));
    }

    private AppUser owner(Long contractorId) {
        return users.findByContractorId(contractorId).stream()
                .filter(u -> u.getRole() == Role.CONTRACTOR).findFirst().orElse(null);
    }

    private static void apply(Contractor c, String name, String ownerName, String phone, String email, String address) {
        c.setName(name.trim());
        c.setOwnerName(ownerName);
        c.setPhone(phone);
        c.setEmail(email);
        c.setAddress(address);
    }

    private static ContractorDto toDto(Contractor c, AppUser u) {
        return new ContractorDto(c.getId(), c.getName(), c.getOwnerName(), c.getPhone(), c.getEmail(), c.getAddress(),
                c.getStatus(), c.getPlan(), c.getBlockedReason(), u != null ? u.getUsername() : null,
                u != null ? u.getLastLoginAt() : null, c.getCreatedAt());
    }
}
