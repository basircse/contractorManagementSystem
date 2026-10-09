package com.ccms.attendance;

import com.ccms.attendance.AttendanceRepositories.AllocationRepository;
import com.ccms.attendance.AttendanceRepositories.AttendanceRepository;
import com.ccms.audit.AuditService;
import com.ccms.catalog.WorkItemController.WorkItemRepository;
import com.ccms.common.ApiException;
import com.ccms.common.ErrorCode;
import com.ccms.common.TenantGuard;
import com.ccms.costing.CostEntry;
import com.ccms.costing.CostEntryRepository;
import com.ccms.costing.WageCalculator;
import com.ccms.hierarchy.HierarchyService;
import com.ccms.hierarchy.Location;
import com.ccms.labour.Labour;
import com.ccms.labour.LabourRepositories.LabourRepository;
import com.ccms.labour.LabourRepositories.RateCardRepository;
import com.ccms.labour.RateCard;
import com.ccms.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Daily attendance tagged to work items (FR-2.2). Every change re-posts the day to the cost
 * ledger: open entries are reversed and fresh ones posted from the current allocations.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceService {

    public static final BigDecimal FULL_DAY = BigDecimal.ONE;

    private final AttendanceRepository attendances;
    private final AllocationRepository allocations;
    private final LabourRepository labours;
    private final RateCardRepository rateCards;
    private final WorkItemRepository workItems;
    private final CostEntryRepository ledger;
    private final HierarchyService hierarchy;
    private final AuditService audit;

    /** One slice of a day, as entered by the user. */
    public record Slice(Long siteId, Long buildingId, Long floorId, Long unitId, Long workItemId,
                        BigDecimal dayFraction, BigDecimal otHours) {
    }

    public record AllocationDto(Long id, Long siteId, Long buildingId, Long floorId, Long unitId, Long workItemId,
                                BigDecimal dayFraction, BigDecimal otHours, String locationLabel) {
    }

    public record AttendanceDto(Long id, Long labourId, String labourName, String skillTier, LocalDate workDate,
                                String note, BigDecimal totalFraction, BigDecimal totalOtHours, BigDecimal cost,
                                List<AllocationDto> allocations) {
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<AttendanceDto> day(LocalDate date, Long siteId) {
        List<Attendance> rows = attendances.findByWorkDate(date);
        List<AttendanceDto> dtos = toDtos(rows);
        if (siteId == null) {
            return dtos;
        }
        return dtos.stream()
                .filter(d -> d.allocations().stream().anyMatch(a -> siteId.equals(a.siteId())))
                .toList();
    }

    // ------------------------------------------------------------------ commands

    /**
     * Quick tagging: add the same slice (location + work item + fraction) to many workers'
     * day at once. Fails as a whole if any worker would exceed one full day.
     */
    public List<AttendanceDto> assign(LocalDate date, Collection<Long> labourIds, Slice slice) {
        Map<Long, Labour> workers = loadLabours(labourIds);
        Slice resolved = resolve(slice);

        Map<Long, Attendance> existing = attendances.findByLabourIdInAndWorkDate(workers.keySet(), date).stream()
                .collect(Collectors.toMap(Attendance::getLabourId, a -> a));
        Map<Long, List<AttendanceAllocation>> existingAllocs = allocations
                .findByAttendanceIdIn(existing.values().stream().map(Attendance::getId).toList()).stream()
                .collect(Collectors.groupingBy(AttendanceAllocation::getAttendanceId));

        List<String> over = new ArrayList<>();
        for (Labour l : workers.values()) {
            Attendance a = existing.get(l.getId());
            BigDecimal used = a == null ? BigDecimal.ZERO : sumFraction(existingAllocs.getOrDefault(a.getId(), List.of()));
            if (used.add(resolved.dayFraction()).compareTo(FULL_DAY) > 0) {
                over.add(l.getName());
            }
        }
        if (!over.isEmpty()) {
            throw new ApiException(ErrorCode.DAY_FRACTION_EXCEEDED, "More than one full day for: " + String.join(", ", over),
                    Map.of("names", over));
        }

        List<Attendance> touched = new ArrayList<>();
        for (Labour l : workers.values()) {
            Attendance a = existing.get(l.getId());
            if (a == null) {
                a = new Attendance();
                a.setLabourId(l.getId());
                a.setWorkDate(date);
                a = attendances.save(a);
            }
            allocations.save(newAllocation(a.getId(), resolved));
            repost(a, l);
            touched.add(a);
        }
        audit.log("ATTENDANCE_ASSIGNED", "ATTENDANCE", null,
                date + " item=" + resolved.workItemId() + " workers=" + workers.keySet());
        return toDtos(touched);
    }

    /** Replace one worker's whole day (edit dialog). An empty slice list removes the day. */
    public AttendanceDto replaceDay(Long labourId, LocalDate date, String note, List<Slice> slices) {
        Labour labour = TenantGuard.own(labours.findById(labourId), "Labour", labourId);
        List<Slice> resolved = slices.stream().map(this::resolve).toList();
        BigDecimal total = resolved.stream().map(Slice::dayFraction).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(FULL_DAY) > 0) {
            throw new ApiException(ErrorCode.DAY_FRACTION_EXCEEDED, "More than one full day",
                    Map.of("names", List.of(labour.getName())));
        }

        Optional<Attendance> current = attendances.findByLabourIdAndWorkDate(labourId, date);
        if (resolved.isEmpty()) {
            current.ifPresent(this::removeDay);
            return null;
        }
        Attendance a = current.orElseGet(() -> {
            Attendance n = new Attendance();
            n.setLabourId(labourId);
            n.setWorkDate(date);
            return n;
        });
        a.setNote(note);
        a = attendances.save(a);
        allocations.deleteByAttendanceId(a.getId());
        for (Slice s : resolved) {
            allocations.save(newAllocation(a.getId(), s));
        }
        repost(a, labour);
        audit.log("ATTENDANCE_EDITED", "ATTENDANCE", a.getId(), date + " labour=" + labourId);
        return toDtos(List.of(a)).get(0);
    }

    public void delete(Long attendanceId) {
        Attendance a = TenantGuard.own(attendances.findById(attendanceId), "Attendance", attendanceId);
        removeDay(a);
    }

    private void removeDay(Attendance a) {
        reverseOpen(a.getId());
        allocations.deleteByAttendanceId(a.getId());
        attendances.delete(a);
        audit.log("ATTENDANCE_DELETED", "ATTENDANCE", a.getId(), a.getWorkDate() + " labour=" + a.getLabourId());
    }

    // ------------------------------------------------------------------ ledger posting

    private void repost(Attendance a, Labour labour) {
        reverseOpen(a.getId());
        Long userId = CurrentUser.userIdOrNull();
        for (AttendanceAllocation al : allocations.findByAttendanceId(a.getId())) {
            RateCard rate = rateFor(labour, al.getSiteId(), a.getWorkDate());
            CostEntry e = new CostEntry();
            e.setSourceType(CostEntry.SourceType.LABOUR);
            e.setSourceId(a.getId());
            e.setEntryDate(a.getWorkDate());
            e.setSiteId(al.getSiteId());
            e.setBuildingId(al.getBuildingId());
            e.setFloorId(al.getFloorId());
            e.setUnitId(al.getUnitId());
            e.setWorkItemId(al.getWorkItemId());
            e.setLabourId(labour.getId());
            e.setDays(al.getDayFraction());
            e.setOtHours(al.getOtHours());
            e.setDailyRate(rate.getDailyRate());
            e.setSkillAllowance(rate.getSkillAllowance());
            e.setOtHourlyRate(rate.getOtHourlyRate());
            e.setAmount(WageCalculator.cost(al.getDayFraction(), al.getOtHours(),
                    rate.getDailyRate(), rate.getSkillAllowance(), rate.getOtHourlyRate()));
            e.setCreatedBy(userId);
            ledger.save(e);
        }
    }

    private void reverseOpen(Long attendanceId) {
        Long userId = CurrentUser.userIdOrNull();
        for (CostEntry e : ledger.openEntries(CostEntry.SourceType.LABOUR, attendanceId)) {
            ledger.save(e.reversal(userId));
        }
    }

    private RateCard rateFor(Labour labour, Long siteId, LocalDate date) {
        return rateCards.candidates(labour.getSkillTier(), siteId, date).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.RATE_NOT_FOUND,
                        "No wage rate for " + labour.getSkillTier() + " on " + date,
                        Map.of("tier", labour.getSkillTier().name(), "date", date.toString(), "name", labour.getName())));
    }

    // ------------------------------------------------------------------ helpers

    private Slice resolve(Slice s) {
        if (s.workItemId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Work item is required");
        }
        TenantGuard.own(workItems.findById(s.workItemId()), "WorkItem", s.workItemId());
        BigDecimal fraction = s.dayFraction() == null ? FULL_DAY : s.dayFraction();
        BigDecimal ot = s.otHours() == null ? BigDecimal.ZERO : s.otHours();
        if (fraction.signum() < 0 || ot.signum() < 0 || ot.compareTo(BigDecimal.valueOf(24)) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid day fraction or OT hours");
        }
        if (fraction.signum() == 0 && ot.signum() == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Day fraction or OT hours required");
        }
        Location loc = hierarchy.resolve(s.siteId(), s.buildingId(), s.floorId(), s.unitId());
        return new Slice(loc.siteId(), loc.buildingId(), loc.floorId(), loc.unitId(), s.workItemId(), fraction, ot);
    }

    private Map<Long, Labour> loadLabours(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Select at least one worker");
        }
        Map<Long, Labour> map = new LinkedHashMap<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            map.put(id, TenantGuard.own(labours.findById(id), "Labour", id));
        }
        return map;
    }

    private static AttendanceAllocation newAllocation(Long attendanceId, Slice s) {
        AttendanceAllocation al = new AttendanceAllocation();
        al.setAttendanceId(attendanceId);
        al.setSiteId(s.siteId());
        al.setBuildingId(s.buildingId());
        al.setFloorId(s.floorId());
        al.setUnitId(s.unitId());
        al.setWorkItemId(s.workItemId());
        al.setDayFraction(s.dayFraction());
        al.setOtHours(s.otHours());
        return al;
    }

    private static BigDecimal sumFraction(List<AttendanceAllocation> list) {
        return list.stream().map(AttendanceAllocation::getDayFraction).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<AttendanceDto> toDtos(List<Attendance> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(Attendance::getId).toList();
        Map<Long, List<AttendanceAllocation>> allocs = allocations.findByAttendanceIdIn(ids).stream()
                .collect(Collectors.groupingBy(AttendanceAllocation::getAttendanceId));
        Map<Long, BigDecimal> cost = new HashMap<>();
        for (Object[] r : ledger.netBySource(CostEntry.SourceType.LABOUR, ids)) {
            cost.put((Long) r[0], (BigDecimal) r[1]);
        }
        Map<Long, Labour> workers = labours.findAllById(rows.stream().map(Attendance::getLabourId).toList()).stream()
                .collect(Collectors.toMap(Labour::getId, l -> l));

        return rows.stream().map(a -> {
            List<AttendanceAllocation> list = allocs.getOrDefault(a.getId(), List.of());
            Labour l = workers.get(a.getLabourId());
            return new AttendanceDto(a.getId(), a.getLabourId(), l != null ? l.getName() : null,
                    l != null ? l.getSkillTier().name() : null, a.getWorkDate(), a.getNote(),
                    sumFraction(list),
                    list.stream().map(AttendanceAllocation::getOtHours).reduce(BigDecimal.ZERO, BigDecimal::add),
                    cost.getOrDefault(a.getId(), BigDecimal.ZERO),
                    list.stream().map(x -> new AllocationDto(x.getId(), x.getSiteId(), x.getBuildingId(), x.getFloorId(),
                            x.getUnitId(), x.getWorkItemId(), x.getDayFraction(), x.getOtHours(),
                            hierarchy.label(x.getSiteId(), x.getBuildingId(), x.getFloorId(), x.getUnitId()))).toList());
        }).sorted(Comparator.comparing(d -> d.labourName() == null ? "" : d.labourName())).toList();
    }
}
