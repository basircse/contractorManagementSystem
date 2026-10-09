package com.ccms.expense;

import com.ccms.catalog.MaterialController.MaterialRepository;
import com.ccms.catalog.WorkItemController.WorkItemRepository;
import com.ccms.common.TenantGuard;
import com.ccms.expense.ExpenseRepositories.PartyRepository;
import com.ccms.hierarchy.HierarchyService;
import com.ccms.hierarchy.Location;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/** Shared lookups and validation for the expense documents. */
@Component
@RequiredArgsConstructor
public class ExpenseSupport {

    private final PartyRepository parties;
    private final MaterialRepository materials;
    private final WorkItemRepository workItems;
    private final HierarchyService hierarchy;

    /** Where a cost belongs; work item optional (general site cost when absent). */
    public record Place(Long siteId, Long buildingId, Long floorId, Long unitId, Long workItemId) {
    }

    public void place(LocatedEntity e, Place p) {
        Location loc = hierarchy.resolve(p.siteId(), p.buildingId(), p.floorId(), p.unitId());
        if (p.workItemId() != null) {
            TenantGuard.own(workItems.findById(p.workItemId()), "WorkItem", p.workItemId());
        }
        e.setLocation(loc);
        e.setWorkItemId(p.workItemId());
    }

    public Party party(Long id) {
        return TenantGuard.own(parties.findById(id), "Party", id);
    }

    public void checkMaterial(Long id) {
        if (id != null) {
            TenantGuard.own(materials.findById(id), "Material", id);
        }
    }

    public Map<Long, String> partyNames() {
        return parties.findAll().stream().collect(Collectors.toMap(Party::getId, Party::getName));
    }

    public String label(LocatedEntity e) {
        return hierarchy.label(e.getSiteId(), e.getBuildingId(), e.getFloorId(), e.getUnitId());
    }
}
