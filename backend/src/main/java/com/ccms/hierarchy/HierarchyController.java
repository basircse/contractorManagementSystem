package com.ccms.hierarchy;

import com.ccms.audit.AuditService;
import com.ccms.common.RecordStatus;
import com.ccms.hierarchy.HierarchyRepositories.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class HierarchyController {

    private final HierarchyService service;
    private final ClientRepository clients;
    private final SiteRepository sites;
    private final BuildingRepository buildings;
    private final FloorRepository floors;
    private final UnitRepository units;
    private final AuditService audit;

    public record ClientRequest(@NotBlank @Size(max = 150) String name, @Size(max = 40) String code,
                                @Size(max = 30) String phone, @Email @Size(max = 150) String email,
                                @Size(max = 500) String address, @Size(max = 1000) String description) {
    }

    public record SiteRequest(@NotNull Long clientId, @NotBlank @Size(max = 150) String name, @Size(max = 40) String code,
                              @Size(max = 500) String address, LocalDate startDate, @Size(max = 1000) String description) {
    }

    public record BuildingRequest(@NotNull Long siteId, @NotBlank @Size(max = 150) String name,
                                  @Size(max = 40) String code, @Size(max = 1000) String description) {
    }

    public record FloorRequest(@NotNull Long buildingId, @NotBlank @Size(max = 150) String name,
                               @Size(max = 40) String code, Integer levelNo, @Size(max = 1000) String description) {
    }

    public record UnitRequest(@NotNull Long floorId, @NotBlank @Size(max = 150) String name,
                              @Size(max = 40) String code, @Size(max = 1000) String description) {
    }

    // ------------------------------------------------------------------ clients

    @GetMapping("/clients")
    public List<Client> listClients(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return filter(clients.findAllByOrderByNameAsc(), includeArchived);
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Client createClient(@Valid @RequestBody ClientRequest req) {
        return saveClient(new Client(), req);
    }

    @PutMapping("/clients/{id}")
    @Transactional
    public Client updateClient(@PathVariable Long id, @Valid @RequestBody ClientRequest req) {
        return saveClient(service.client(id), req);
    }

    private Client saveClient(Client c, ClientRequest req) {
        c.setName(req.name().trim());
        c.setCode(req.code());
        c.setPhone(req.phone());
        c.setEmail(req.email());
        c.setAddress(req.address());
        c.setDescription(req.description());
        return clients.save(c);
    }

    // ------------------------------------------------------------------ sites

    @GetMapping("/sites")
    public List<Site> listSites(@RequestParam(required = false) Long clientId,
                                @RequestParam(defaultValue = "false") boolean includeArchived) {
        return filter(clientId == null ? sites.findAllByOrderByNameAsc() : sites.findByClientIdOrderByNameAsc(clientId),
                includeArchived);
    }

    @PostMapping("/sites")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Site createSite(@Valid @RequestBody SiteRequest req) {
        return saveSite(new Site(), req);
    }

    @PutMapping("/sites/{id}")
    @Transactional
    public Site updateSite(@PathVariable Long id, @Valid @RequestBody SiteRequest req) {
        return saveSite(service.site(id), req);
    }

    private Site saveSite(Site s, SiteRequest req) {
        service.client(req.clientId());
        s.setClientId(req.clientId());
        s.setName(req.name().trim());
        s.setCode(req.code());
        s.setAddress(req.address());
        s.setStartDate(req.startDate());
        s.setDescription(req.description());
        return sites.save(s);
    }

    // ------------------------------------------------------------------ buildings

    @GetMapping("/buildings")
    public List<Building> listBuildings(@RequestParam Long siteId,
                                        @RequestParam(defaultValue = "false") boolean includeArchived) {
        return filter(buildings.findBySiteIdOrderByNameAsc(siteId), includeArchived);
    }

    @PostMapping("/buildings")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Building createBuilding(@Valid @RequestBody BuildingRequest req) {
        return saveBuilding(new Building(), req);
    }

    @PutMapping("/buildings/{id}")
    @Transactional
    public Building updateBuilding(@PathVariable Long id, @Valid @RequestBody BuildingRequest req) {
        return saveBuilding(service.building(id), req);
    }

    private Building saveBuilding(Building b, BuildingRequest req) {
        service.site(req.siteId());
        b.setSiteId(req.siteId());
        b.setName(req.name().trim());
        b.setCode(req.code());
        b.setDescription(req.description());
        return buildings.save(b);
    }

    // ------------------------------------------------------------------ floors

    @GetMapping("/floors")
    public List<Floor> listFloors(@RequestParam Long buildingId,
                                  @RequestParam(defaultValue = "false") boolean includeArchived) {
        return filter(floors.findByBuildingIdOrderByLevelNoAscNameAsc(buildingId), includeArchived);
    }

    @PostMapping("/floors")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Floor createFloor(@Valid @RequestBody FloorRequest req) {
        return saveFloor(new Floor(), req);
    }

    @PutMapping("/floors/{id}")
    @Transactional
    public Floor updateFloor(@PathVariable Long id, @Valid @RequestBody FloorRequest req) {
        return saveFloor(service.floor(id), req);
    }

    private Floor saveFloor(Floor f, FloorRequest req) {
        service.building(req.buildingId());
        f.setBuildingId(req.buildingId());
        f.setName(req.name().trim());
        f.setCode(req.code());
        f.setLevelNo(req.levelNo());
        f.setDescription(req.description());
        return floors.save(f);
    }

    // ------------------------------------------------------------------ units

    @GetMapping("/units")
    public List<Unit> listUnits(@RequestParam Long floorId,
                                @RequestParam(defaultValue = "false") boolean includeArchived) {
        return filter(units.findByFloorIdOrderByNameAsc(floorId), includeArchived);
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Unit createUnit(@Valid @RequestBody UnitRequest req) {
        return saveUnit(new Unit(), req);
    }

    @PutMapping("/units/{id}")
    @Transactional
    public Unit updateUnit(@PathVariable Long id, @Valid @RequestBody UnitRequest req) {
        return saveUnit(service.unit(id), req);
    }

    private Unit saveUnit(Unit u, UnitRequest req) {
        service.floor(req.floorId());
        u.setFloorId(req.floorId());
        u.setName(req.name().trim());
        u.setCode(req.code());
        u.setDescription(req.description());
        return units.save(u);
    }

    // ------------------------------------------------------------------ archive / restore (any level)

    @PostMapping("/{level:clients|sites|buildings|floors|units}/{id}/archive")
    public void archive(@PathVariable String level, @PathVariable Long id) {
        service.setStatus(level, id, RecordStatus.ARCHIVED);
        audit.log("ARCHIVED", level, id, null);
    }

    @PostMapping("/{level:clients|sites|buildings|floors|units}/{id}/restore")
    public void restore(@PathVariable String level, @PathVariable Long id) {
        service.setStatus(level, id, RecordStatus.ACTIVE);
        audit.log("RESTORED", level, id, null);
    }

    private static <T extends HierarchyNode> List<T> filter(List<T> rows, boolean includeArchived) {
        return includeArchived ? rows : rows.stream().filter(r -> r.getStatus() == RecordStatus.ACTIVE).toList();
    }
}
