package com.ccms.platform;

import com.ccms.common.ApiException;
import com.ccms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The current business's letterhead details, used on printed quotations and bills. */
@RestController
@RequestMapping("/api/app/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ContractorRepository contractors;

    public record Profile(Long id, String name, String ownerName, String phone, String email, String address) {
    }

    @GetMapping
    public Profile get() {
        Long id = TenantContext.require();
        Contractor c = contractors.findById(id).orElseThrow(() -> ApiException.notFound("Contractor", id));
        return new Profile(c.getId(), c.getName(), c.getOwnerName(), c.getPhone(), c.getEmail(), c.getAddress());
    }
}
