package com.ccms.expense;

import com.ccms.audit.AuditService;
import com.ccms.common.ApiException;
import com.ccms.common.ErrorCode;
import com.ccms.common.Money;
import com.ccms.common.TenantGuard;
import com.ccms.costing.CostEntry;
import com.ccms.costing.LedgerPoster;
import com.ccms.costing.LedgerPoster.Posting;
import com.ccms.expense.ExpenseRepositories.SubcontractBillRepository;
import com.ccms.expense.ExpenseRepositories.SubcontractRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Third-party work orders given out by the contractor, and the bills they raise for work done. */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class SubcontractController {

    private final SubcontractRepository subcontracts;
    private final SubcontractBillRepository bills;
    private final ExpenseSupport support;
    private final LedgerPoster ledger;
    private final AuditService audit;

    public record SubcontractRequest(@NotNull Long partyId, @NotBlank @Size(max = 200) String title,
                                     @NotNull Long siteId, Long buildingId, Long floorId, Long unitId, Long workItemId,
                                     @Size(max = 20) String uom,
                                     @PositiveOrZero @Digits(integer = 11, fraction = 3) BigDecimal quantity,
                                     @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal rate,
                                     /* lump sum when quantity x rate is not used */
                                     @PositiveOrZero @Digits(integer = 14, fraction = 2) BigDecimal contractAmount,
                                     LocalDate startDate, @Size(max = 1000) String note) {
    }

    public record StatusRequest(@NotNull Subcontract.Status status) {
    }

    public record BillRequest(@NotNull LocalDate billDate,
                              @PositiveOrZero @Digits(integer = 11, fraction = 3) BigDecimal quantity,
                              /* null = quantity x agreed rate */
                              @PositiveOrZero @Digits(integer = 14, fraction = 2) BigDecimal amount,
                              @Size(max = 500) String note) {
    }

    public record SubcontractView(@JsonUnwrapped Subcontract subcontract, String partyName, String locationLabel,
                                  BigDecimal billedQty, BigDecimal billedAmount, BigDecimal remaining,
                                  List<SubcontractBill> bills) {
    }

    @GetMapping("/subcontracts")
    @Transactional(readOnly = true)
    public List<SubcontractView> list(@RequestParam(required = false) Long partyId,
                                      @RequestParam(required = false) Long siteId,
                                      @RequestParam(required = false) Subcontract.Status status) {
        Map<Long, Object[]> totals = new HashMap<>();
        for (Object[] r : bills.totalsBySubcontract()) totals.put((Long) r[0], r);
        Map<Long, String> parties = support.partyNames();
        return subcontracts.findAllByOrderByStatusAscIdDesc().stream()
                .filter(s -> partyId == null || partyId.equals(s.getPartyId()))
                .filter(s -> siteId == null || siteId.equals(s.getSiteId()))
                .filter(s -> status == null || status == s.getStatus())
                .map(s -> {
                    Object[] t = totals.get(s.getId());
                    BigDecimal qty = t != null && t[1] != null ? (BigDecimal) t[1] : BigDecimal.ZERO;
                    BigDecimal amt = t != null ? Money.of((BigDecimal) t[2]) : Money.of(null);
                    return new SubcontractView(s, parties.get(s.getPartyId()), support.label(s), qty, amt,
                            Money.of(s.getContractAmount().subtract(amt)), null);
                })
                .toList();
    }

    @GetMapping("/subcontracts/{id}")
    @Transactional(readOnly = true)
    public SubcontractView get(@PathVariable Long id) {
        return view(find(id));
    }

    @PostMapping("/subcontracts")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SubcontractView create(@Valid @RequestBody SubcontractRequest req) {
        Subcontract s = apply(new Subcontract(), req);
        audit.log("SUBCONTRACT_CREATED", "SUBCONTRACT", s.getId(), s.getTitle() + " " + s.getContractAmount());
        return view(s);
    }

    /** Location / work item changes re-post the bills already recorded. */
    @PutMapping("/subcontracts/{id}")
    @Transactional
    public SubcontractView update(@PathVariable Long id, @Valid @RequestBody SubcontractRequest req) {
        Subcontract s = apply(find(id), req);
        for (SubcontractBill b : bills.findBySubcontractIdOrderByBillDateAscIdAsc(id)) {
            post(s, b);
        }
        audit.log("SUBCONTRACT_UPDATED", "SUBCONTRACT", id, s.getTitle() + " " + s.getContractAmount());
        return view(s);
    }

    @PostMapping("/subcontracts/{id}/status")
    @Transactional
    public SubcontractView status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        Subcontract s = find(id);
        s.setStatus(req.status());
        audit.log("SUBCONTRACT_STATUS", "SUBCONTRACT", id, req.status().name());
        return view(s);
    }

    @DeleteMapping("/subcontracts/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        Subcontract s = find(id);
        if (bills.existsBySubcontractId(id)) {
            throw IncomeSupport.invalidState("HAS_BILLS", "Sub-contract has bills");
        }
        subcontracts.delete(s);
        audit.log("SUBCONTRACT_DELETED", "SUBCONTRACT", id, s.getTitle());
    }

    @PostMapping("/subcontracts/{id}/bills")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SubcontractView addBill(@PathVariable Long id, @Valid @RequestBody BillRequest req) {
        Subcontract s = find(id);
        if (s.getStatus() == Subcontract.Status.CANCELLED) {
            throw IncomeSupport.invalidState("SUBCONTRACT_CLOSED", "Sub-contract is cancelled");
        }
        BigDecimal amount = req.amount() != null ? Money.of(req.amount())
                : s.getRate() != null && req.quantity() != null ? Money.times(req.quantity(), s.getRate()) : null;
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount is required");
        }
        SubcontractBill b = new SubcontractBill();
        b.setSubcontractId(id);
        b.setBillDate(req.billDate());
        b.setQuantity(req.quantity());
        b.setAmount(amount);
        b.setNote(req.note());
        bills.save(b);
        post(s, b);
        audit.log("SUBCONTRACT_BILL", "SUBCONTRACT", id, amount.toString());
        return view(s);
    }

    @DeleteMapping("/subcontract-bills/{billId}")
    @Transactional
    public SubcontractView deleteBill(@PathVariable Long billId) {
        SubcontractBill b = TenantGuard.own(bills.findById(billId), "SubcontractBill", billId);
        ledger.reverse(CostEntry.SourceType.SUBCONTRACT, billId);
        bills.delete(b);
        audit.log("SUBCONTRACT_BILL_DELETED", "SUBCONTRACT", b.getSubcontractId(), b.getAmount().toString());
        return view(find(b.getSubcontractId()));
    }

    // ------------------------------------------------------------------ helpers

    private void post(Subcontract s, SubcontractBill b) {
        ledger.reverse(CostEntry.SourceType.SUBCONTRACT, b.getId());
        ledger.post(new Posting(CostEntry.SourceType.SUBCONTRACT, b.getId(), b.getBillDate(), s.location(),
                s.getWorkItemId(), s.getPartyId(), null, b.getAmount()));
    }

    private Subcontract apply(Subcontract s, SubcontractRequest req) {
        support.party(req.partyId());
        support.place(s, new Place(req.siteId(), req.buildingId(), req.floorId(), req.unitId(), req.workItemId()));
        s.setPartyId(req.partyId());
        s.setTitle(req.title().trim());
        s.setUom(req.uom() == null || req.uom().isBlank() ? null : req.uom().trim().toUpperCase());
        s.setQuantity(req.quantity());
        s.setRate(req.rate());
        BigDecimal amount = req.quantity() != null && req.rate() != null && req.quantity().signum() > 0
                ? Money.times(req.quantity(), req.rate()) : Money.of(req.contractAmount());
        s.setContractAmount(amount);
        s.setStartDate(req.startDate());
        s.setNote(req.note());
        return subcontracts.save(s);
    }

    private SubcontractView view(Subcontract s) {
        List<SubcontractBill> list = bills.findBySubcontractIdOrderByBillDateAscIdAsc(s.getId());
        BigDecimal qty = list.stream().map(SubcontractBill::getQuantity).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amt = Money.sum(list, SubcontractBill::getAmount);
        return new SubcontractView(s, support.party(s.getPartyId()).getName(), support.label(s), qty, amt,
                Money.of(s.getContractAmount().subtract(amt)), list);
    }

    private Subcontract find(Long id) {
        return TenantGuard.own(subcontracts.findById(id), "Subcontract", id);
    }
}
