package com.ccms.hierarchy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repositories for the hierarchy. All queries are tenant-filtered by Hibernate. */
public final class HierarchyRepositories {

    private HierarchyRepositories() {
    }

    public interface ClientRepository extends JpaRepository<Client, Long> {
        List<Client> findAllByOrderByNameAsc();
    }

    public interface SiteRepository extends JpaRepository<Site, Long> {
        List<Site> findAllByOrderByNameAsc();

        List<Site> findByClientIdOrderByNameAsc(Long clientId);
    }

    public interface BuildingRepository extends JpaRepository<Building, Long> {
        List<Building> findBySiteIdOrderByNameAsc(Long siteId);
    }

    public interface FloorRepository extends JpaRepository<Floor, Long> {
        List<Floor> findByBuildingIdOrderByLevelNoAscNameAsc(Long buildingId);
    }

    public interface UnitRepository extends JpaRepository<Unit, Long> {
        List<Unit> findByFloorIdOrderByNameAsc(Long floorId);
    }
}
