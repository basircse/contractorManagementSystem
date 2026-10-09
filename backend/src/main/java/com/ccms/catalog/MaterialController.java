package com.ccms.catalog;

import com.ccms.common.ApiException;
import com.ccms.common.ErrorCode;
import com.ccms.common.TenantGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/app/materials")
@RequiredArgsConstructor
public class MaterialController {

    public interface MaterialRepository extends JpaRepository<Material, Long> {
        List<Material> findAllByOrderBySortOrderAscNameEnAsc();

        boolean existsByCodeIgnoreCase(String code);
    }

    private final MaterialRepository repo;

    public record MaterialRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_]+") String code,
                                  @NotBlank @Size(max = 150) String nameBn,
                                  @NotBlank @Size(max = 150) String nameEn,
                                  @NotBlank @Size(max = 20) String uom,
                                  @NotNull Material.Kind kind,
                                  Integer sortOrder,
                                  Boolean active) {
    }

    @GetMapping
    public List<Material> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return repo.findAllByOrderBySortOrderAscNameEnAsc().stream()
                .filter(m -> includeInactive || m.isActive())
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Material create(@Valid @RequestBody MaterialRequest req) {
        if (repo.existsByCodeIgnoreCase(req.code())) {
            throw new ApiException(ErrorCode.DUPLICATE, "Code already exists", Map.of("field", "code"));
        }
        Material m = new Material();
        m.setCode(req.code().toUpperCase());
        return apply(m, req);
    }

    @PutMapping("/{id}")
    @Transactional
    public Material update(@PathVariable Long id, @Valid @RequestBody MaterialRequest req) {
        return apply(find(id), req);
    }

    public Material find(Long id) {
        return TenantGuard.own(repo.findById(id), "Material", id);
    }

    private Material apply(Material m, MaterialRequest req) {
        m.setNameBn(req.nameBn().trim());
        m.setNameEn(req.nameEn().trim());
        m.setUom(req.uom().toUpperCase());
        m.setKind(req.kind());
        if (req.sortOrder() != null) {
            m.setSortOrder(req.sortOrder());
        }
        if (req.active() != null) {
            m.setActive(req.active());
        }
        return repo.save(m);
    }
}
