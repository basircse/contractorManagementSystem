package com.ccms.catalog;

import com.ccms.common.ApiException;
import com.ccms.common.ErrorCode;
import com.ccms.common.TenantGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/app/work-items")
@RequiredArgsConstructor
public class WorkItemController {

    public interface WorkItemRepository extends JpaRepository<WorkItem, Long> {
        List<WorkItem> findAllByOrderBySortOrderAscNameEnAsc();

        boolean existsByCodeIgnoreCase(String code);
    }

    private final WorkItemRepository repo;

    public record WorkItemRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_]+") String code,
                                  @NotBlank @Size(max = 150) String nameBn,
                                  @NotBlank @Size(max = 150) String nameEn,
                                  @NotBlank @Size(max = 20) String uom,
                                  Integer sortOrder,
                                  Boolean active) {
    }

    @GetMapping
    public List<WorkItem> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return repo.findAllByOrderBySortOrderAscNameEnAsc().stream()
                .filter(w -> includeInactive || w.isActive())
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public WorkItem create(@Valid @RequestBody WorkItemRequest req) {
        if (repo.existsByCodeIgnoreCase(req.code())) {
            throw new ApiException(ErrorCode.DUPLICATE, "Code already exists", Map.of("field", "code"));
        }
        WorkItem w = new WorkItem();
        w.setCode(req.code().toUpperCase());
        return apply(w, req);
    }

    @PutMapping("/{id}")
    @Transactional
    public WorkItem update(@PathVariable Long id, @Valid @RequestBody WorkItemRequest req) {
        // The code is the stable key used in reports, so it is not editable.
        return apply(TenantGuard.own(repo.findById(id), "WorkItem", id), req);
    }

    private WorkItem apply(WorkItem w, WorkItemRequest req) {
        w.setNameBn(req.nameBn().trim());
        w.setNameEn(req.nameEn().trim());
        w.setUom(req.uom().toUpperCase());
        if (req.sortOrder() != null) {
            w.setSortOrder(req.sortOrder());
        }
        if (req.active() != null) {
            w.setActive(req.active());
        }
        return repo.save(w);
    }
}
