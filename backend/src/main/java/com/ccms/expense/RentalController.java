package com.ccms.expense;

import com.ccms.audit.AuditService;
import com.ccms.common.ApiException;
import com.ccms.common.ErrorCode;
import com.ccms.common.Money;
import com.ccms.common.TenantGuard;
import com.ccms.costing.CostEntry;
import com.ccms.costing.LedgerPoster;
import com.ccms.costing.LedgerPoster.Posting;
import com.ccms.expense.ExpenseRepositories.RentalEventRepository;
import com.ccms.expense.ExpenseRepositories.RentalRepository;
import com.ccms.expense.ExpenseSupport.Place;
import com.ccms.income.IncomeSupport;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hired / borrowed items (shuttering, props, bamboo, pins ...). Rent accrues per unit per day
 * and is posted to the ledger whenever it is charged (e.g. monthly) and when items go back.
 */
@RestController
@RequestMapping("/api/app/rentals")
@RequiredArgsConstructor
public class RentalController {

    private final RentalRepository rentals;
    private final RentalEventRepository events;
    private final ExpenseSupport support;
    private final LedgerPoster ledger;
    private final AuditService audit;

    public record RentalRequest(Long partyId, Long materialId, @Size(max = 300) String description,
                                @Size(max = 20) String uom,
                                @NotNull Long siteId, Long buildingId, Long floorId, Long unitId, Long workItemId,
                                @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity,
                                @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 4) BigDecimal ratePerDay,
                                @NotNull LocalDate startDate, @Size(max = 500) String note) {
    }

    public record ChargeRequest(@NotNull LocalDate date, @Size(max = 500) String note) {
    }

    public record ReturnRequest(@NotNull LocalDate date,
                                @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity,
                                @Size(max = 500) String note) {
    }

    /** accrued = rent since the last charge up to today, not yet in the ledger. */
    public record RentalView(@JsonUnwrapped Rental rental, String partyName, String locationLabel,
                             BigDecimal charged, BigDecimal accrued, long daysOut, List<RentalEvent> events) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RentalView> list(@RequestParam(required = false) Rental.Status status,
                                 @RequestParam(required = false) Long siteId,
                                 @RequestParam(required = false) Long partyId) {
        List<Rental> rows = rentals.findAllByOrderByStatusAscStartDateDescIdDesc().stream()
                .filter(r -> status == null || status == r.getStatus())
                .filter(r -> siteId == null || siteId.equals(r.getSiteId()))
                .filter(r -> partyId == null || partyId.equals(r.getPartyId()))
                .toList();
        Map<Long, List<RentalEvent>> byRental = rows.isEmpty() ? Map.of()
                : events.findByRentalIdIn(rows.stream().map(Rental::getId).toList()).stream()
                .collect(Collectors.groupingBy(RentalEvent::getRentalId));
        Map<Long, String> parties = support.partyNames();
        return rows.stream().map(r -> view(r, parties.get(r.getPartyId()), byRental.getOrDefault(r.getId(), List.of()))).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public RentalView get(@PathVariable Long id) {
        return view(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RentalView create(@Valid @RequestBody RentalRequest req) {
        Rental r = new Rental();
        apply(r, req);
        r.setQuantityOut(req.quantity());
        rentals.save(r);
        audit.log("RENTAL_CREATED", "RENTAL", r.getId(), r.getQuantity() + " x " + r.getRatePerDay());
        return view(r);
    }

    /** Full edit is only possible before any rent was charged or anything returned. */
    @PutMapping("/{id}")
    @Transactional
    public RentalView update(@PathVariable Long id, @Valid @RequestBody RentalRequest req) {
        Rental r = find(id);
        if (!events.findByRentalIdOrderByIdAsc(id).isEmpty()) {
            r.setNote(req.note());
            r.setDescription(req.description());
            return view(r);
        }
        apply(r, req);
        r.setQuantityOut(req.quantity());
        audit.log("RENTAL_UPDATED", "RENTAL", id, r.getQuantity() + " x " + r.getRatePerDay());
        return view(r);
    }

    /** Post rent up to a date (e.g. month end) for everything still out. */
    @PostMapping("/{id}/charge")
    @Transactional
    public RentalView charge(@PathVariable Long id, @Valid @RequestBody ChargeRequest req) {
        Rental r = find(id);
        if (r.getStatus() == Rental.Status.RETURNED) {
            throw IncomeSupport.invalidState("RENTAL_RETURNED", "Everything has been returned");
        }
        RentalEvent e = new RentalEvent();
        e.setType(RentalEvent.Type.CHARGE);
        e.setEventDate(req.date());
        e.setNote(req.note());
        chargeUntil(r, e, req.date());
        if (e.getDays() == 0) {
            throw IncomeSupport.invalidState("NOTHING_TO_CHARGE", "Rent is already charged up to this date");
        }
        audit.log("RENTAL_CHARGED", "RENTAL", id, e.getDays() + " days " + e.getAmount());
        return view(r);
    }

    /** Items going back. Rent for everything still out is charged up to the return date. */
    @PostMapping("/{id}/return")
    @Transactional
    public RentalView giveBack(@PathVariable Long id, @Valid @RequestBody ReturnRequest req) {
        Rental r = find(id);
        if (r.getStatus() == Rental.Status.RETURNED) {
            throw IncomeSupport.invalidState("RENTAL_RETURNED", "Everything has been returned");
        }
        if (req.quantity().compareTo(r.getQuantityOut()) > 0) {
            throw new ApiException(ErrorCode.QUANTITY_EXCEEDED, "Only " + r.getQuantityOut() + " still out",
                    Map.of("max", r.getQuantityOut()));
        }
        RentalEvent e = new RentalEvent();
        e.setType(RentalEvent.Type.RETURN);
        e.setEventDate(req.date());
        e.setQuantity(req.quantity());
        e.setNote(req.note());
        chargeUntil(r, e, req.date());
        r.setQuantityOut(r.getQuantityOut().subtract(req.quantity()));
        if (r.getQuantityOut().signum() == 0) {
            r.setStatus(Rental.Status.RETURNED);
            r.setEndDate(req.date());
        }
        audit.log("RENTAL_RETURNED", "RENTAL", id, req.quantity() + " on " + req.date());
        return view(r);
    }

    /** Undo the latest charge / return. */
    @DeleteMapping("/{id}/events/last")
    @Transactional
    public RentalView undoLast(@PathVariable Long id) {
        Rental r = find(id);
        List<RentalEvent> list = events.findByRentalIdOrderByIdAsc(id);
        if (list.isEmpty()) {
            throw ApiException.notFound("RentalEvent", id);
        }
        RentalEvent last = list.get(list.size() - 1);
        ledger.reverse(CostEntry.SourceType.RENTAL, last.getId());
        if (last.getChargeFrom() != null) {
            r.setChargedUntil(last.getChargeFrom().equals(r.getStartDate()) ? null : last.getChargeFrom().minusDays(1));
        }
        if (last.getType() == RentalEvent.Type.RETURN) {
            r.setQuantityOut(r.getQuantityOut().add(last.getQuantity()));
            r.setStatus(Rental.Status.OUT);
            r.setEndDate(null);
        }
        events.delete(last);
        audit.log("RENTAL_EVENT_UNDONE", "RENTAL", id, last.getType() + " " + last.getEventDate());
        return view(r);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        Rental r = find(id);
        for (RentalEvent e : events.findByRentalIdOrderByIdAsc(id)) {
            ledger.reverse(CostEntry.SourceType.RENTAL, e.getId());
            events.delete(e);
        }
        rentals.delete(r);
        audit.log("RENTAL_DELETED", "RENTAL", id, r.getQuantity() + " x " + r.getRatePerDay());
    }

    // ------------------------------------------------------------------ helpers

    /** Charges rent from the first uncharged day to {@code to} for the quantity out, and saves the event. */
    private void chargeUntil(Rental r, RentalEvent e, LocalDate to) {
        if (to.isBefore(r.getStartDate())) {
            throw IncomeSupport.invalidState("DATE_BEFORE_START", "Date is before the start date");
        }
        if (r.getChargedUntil() != null && to.isBefore(r.getChargedUntil())) {
            throw IncomeSupport.invalidState("DATE_ALREADY_CHARGED", "Rent is already charged beyond this date");
        }
        e.setRentalId(r.getId());
        LocalDate from = r.nextChargeFrom();
        int days = (int) Math.max(0, ChronoUnit.DAYS.between(from, to) + 1);
        if (days > 0) {
            e.setChargeFrom(from);
            e.setChargeTo(to);
            e.setChargedQty(r.getQuantityOut());
            e.setDays(days);
            e.setAmount(Money.of(r.getQuantityOut().multiply(r.getRatePerDay()).multiply(BigDecimal.valueOf(days))));
            r.setChargedUntil(to);
        }
        events.save(e);
        ledger.post(new Posting(CostEntry.SourceType.RENTAL, e.getId(), to, r.location(), r.getWorkItemId(),
                r.getPartyId(), r.getMaterialId(), e.getAmount()));
    }

    private void apply(Rental r, RentalRequest req) {
        if (req.partyId() != null) {
            support.party(req.partyId());
        }
        if (req.materialId() == null && (req.description() == null || req.description().isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Material or description is required");
        }
        support.checkMaterial(req.materialId());
        support.place(r, new Place(req.siteId(), req.buildingId(), req.floorId(), req.unitId(), req.workItemId()));
        r.setPartyId(req.partyId());
        r.setMaterialId(req.materialId());
        r.setDescription(req.description());
        r.setUom(req.uom() == null || req.uom().isBlank() ? null : req.uom().trim().toUpperCase());
        r.setQuantity(req.quantity());
        r.setRatePerDay(req.ratePerDay());
        r.setStartDate(req.startDate());
        r.setNote(req.note());
    }

    private RentalView view(Rental r) {
        return view(r, r.getPartyId() != null ? support.party(r.getPartyId()).getName() : null,
                events.findByRentalIdOrderByIdAsc(r.getId()));
    }

    private RentalView view(Rental r, String partyName, List<RentalEvent> evs) {
        LocalDate today = LocalDate.now();
        BigDecimal charged = Money.sum(evs, RentalEvent::getAmount);
        BigDecimal accrued = BigDecimal.ZERO;
        if (r.getStatus() == Rental.Status.OUT) {
            long days = Math.max(0, ChronoUnit.DAYS.between(r.nextChargeFrom(), today) + 1);
            accrued = Money.of(r.getQuantityOut().multiply(r.getRatePerDay()).multiply(BigDecimal.valueOf(days)));
        }
        LocalDate end = r.getEndDate() != null ? r.getEndDate() : today;
        long daysOut = Math.max(0, ChronoUnit.DAYS.between(r.getStartDate(), end) + 1);
        return new RentalView(r, partyName, support.label(r), charged, accrued, daysOut,
                evs.stream().sorted((a, b) -> Long.compare(a.getId(), b.getId())).toList());
    }

    private Rental find(Long id) {
        return TenantGuard.own(rentals.findById(id), "Rental", id);
    }
}
