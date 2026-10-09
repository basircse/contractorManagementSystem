package com.ccms.hierarchy;

import com.ccms.common.ApiException;
import com.ccms.common.ErrorCode;
import com.ccms.common.RecordStatus;
import com.ccms.common.TenantGuard;
import com.ccms.hierarchy.HierarchyRepositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HierarchyService {

    private final ClientRepository clients;
    private final SiteRepository sites;
    private final BuildingRepository buildings;
    private final FloorRepository floors;
    private final UnitRepository units;

    public Client client(Long id) {
        return TenantGuard.own(clients.findById(id), "Client", id);
    }

    public Site site(Long id) {
        return TenantGuard.own(sites.findById(id), "Site", id);
    }

    public Building building(Long id) {
        return TenantGuard.own(buildings.findById(id), "Building", id);
    }

    public Floor floor(Long id) {
        return TenantGuard.own(floors.findById(id), "Floor", id);
    }

    public Unit unit(Long id) {
        return TenantGuard.own(units.findById(id), "Unit", id);
    }

    public HierarchyNode node(String level, Long id) {
        return switch (level) {
            case "clients" -> client(id);
            case "sites" -> site(id);
            case "buildings" -> building(id);
            case "floors" -> floor(id);
            case "units" -> unit(id);
            default -> throw ApiException.notFound("Level", level);
        };
    }

    @Transactional
    public void setStatus(String level, Long id, RecordStatus status) {
        node(level, id).setStatus(status);
    }

    /**
     * Fills in missing ancestors from the deepest level given and rejects inconsistent
     * combinations (e.g. a floor that belongs to a different building).
     */
    public Location resolve(Long siteId, Long buildingId, Long floorId, Long unitId) {
        if (unitId != null) {
            Unit u = unit(unitId);
            floorId = check(floorId, u.getFloorId());
        }
        if (floorId != null) {
            Floor f = floor(floorId);
            buildingId = check(buildingId, f.getBuildingId());
        }
        if (buildingId != null) {
            Building b = building(buildingId);
            siteId = check(siteId, b.getSiteId());
        }
        if (siteId == null) {
            throw new ApiException(ErrorCode.INVALID_LOCATION, "Site is required");
        }
        site(siteId);
        return new Location(siteId, buildingId, floorId, unitId);
    }

    /** "Site › Building › Floor › Unit" for display. Repeated ids hit the persistence context cache. */
    public String label(Long siteId, Long buildingId, Long floorId, Long unitId) {
        StringBuilder sb = new StringBuilder();
        sites.findById(siteId).ifPresent(s -> sb.append(s.getName()));
        if (buildingId != null) buildings.findById(buildingId).ifPresent(b -> sb.append(" › ").append(b.getName()));
        if (floorId != null) floors.findById(floorId).ifPresent(f -> sb.append(" › ").append(f.getName()));
        if (unitId != null) units.findById(unitId).ifPresent(u -> sb.append(" › ").append(u.getName()));
        return sb.toString();
    }

    private static Long check(Long given, Long actualParent) {
        if (given != null && !Objects.equals(given, actualParent)) {
            throw new ApiException(ErrorCode.INVALID_LOCATION, "Location levels do not match");
        }
        return actualParent;
    }
}
