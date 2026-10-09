package com.ccms.expense;

import com.ccms.common.TenantEntity;
import com.ccms.hierarchy.Location;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/** A cost document tagged to a place in the hierarchy and optionally a work item. */
@Getter
@Setter
@MappedSuperclass
public abstract class LocatedEntity extends TenantEntity {

    @Column(nullable = false)
    private Long siteId;
    private Long buildingId;
    private Long floorId;
    private Long unitId;
    private Long workItemId;

    public Location location() {
        return new Location(siteId, buildingId, floorId, unitId);
    }

    public void setLocation(Location l) {
        siteId = l.siteId();
        buildingId = l.buildingId();
        floorId = l.floorId();
        unitId = l.unitId();
    }
}
