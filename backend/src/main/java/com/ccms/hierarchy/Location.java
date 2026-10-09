package com.ccms.hierarchy;

/** A point in the hierarchy. siteId is always set; deeper levels are optional. */
public record Location(Long siteId, Long buildingId, Long floorId, Long unitId) {
}
