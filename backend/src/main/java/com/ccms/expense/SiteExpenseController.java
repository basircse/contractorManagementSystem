package com.ccms.expense;

import com.ccms.audit.AuditService;
import com.ccms.common.Money;
import com.ccms.common.TenantGuard;
import com.ccms.costing.CostEntry;
import com.ccms.costing.LedgerPoster;
import com.ccms.costing.LedgerPoster.Posting;
import com.ccms.expense.ExpenseRepositories.SiteExpenseRepository;
import com.ccms.expense.ExpenseSupport.Place;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Other site costs: transport, fuel, food, electricity, tools ... */
@RestController
@RequestMapping("/api/app/expenses")
@RequiredArgsConstructor
public class SiteExpenseController {

    private final SiteExpenseRepository expenses;
    private final ExpenseSupport support;
    private final LedgerPoster ledger;
    private final AuditService audit;

    public record ExpenseRequest(@NotNull LocalDate expenseDate, @NotNull SiteExpense.Category category,
                                 /* only when bought on credit from a party */ Long partyId,
                                 @NotNull Long siteId, Long buildingId, Long floorId, Long unitId, Long workItemId,
                                 @NotNull @Positive @Digits(integer = 14, fraction = 2) BigDecimal amount,
                                 @Size(max = 500) String description) {
    }

    public record ExpenseView(@JsonUnwrapped SiteExpense expense, String partyName, String locationLabel) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ExpenseView> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) Long siteId,
                                  @RequestParam(required = false) SiteExpense.Category category) {
        LocalDate end = to != null ? to : LocalDate.now();
        Map<Long, String> parties = support.partyNames();
        return expenses.between(from != null ? from : end.minusDays(30), end).stream()
                .filter(e -> siteId == null || siteId.equals(e.getSiteId()))
                .filter(e -> category == null || category == e.getCategory())
                .map(e -> new ExpenseView(e, parties.get(e.getPartyId()), support.label(e)))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ExpenseView create(@Valid @RequestBody ExpenseRequest req) {
        SiteExpense e = save(new SiteExpense(), req);
        audit.log("EXPENSE_CREATED", "EXPENSE", e.getId(), e.getCategory() + " " + e.getAmount());
        return view(e);
    }

    @PutMapping("/{id}")
    @Transactional
    public ExpenseView update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest req) {
        SiteExpense e = save(find(id), req);
        audit.log("EXPENSE_UPDATED", "EXPENSE", id, e.getCategory() + " " + e.getAmount());
        return view(e);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        SiteExpense e = find(id);
        ledger.reverse(CostEntry.SourceType.EXPENSE, id);
        expenses.delete(e);
        audit.log("EXPENSE_DELETED", "EXPENSE", id, e.getCategory() + " " + e.getAmount());
    }

    private SiteExpense save(SiteExpense e, ExpenseRequest req) {
        if (req.partyId() != null) {
            support.party(req.partyId());
        }
        support.place(e, new Place(req.siteId(), req.buildingId(), req.floorId(), req.unitId(), req.workItemId()));
        e.setExpenseDate(req.expenseDate());
        e.setCategory(req.category());
        e.setPartyId(req.partyId());
        e.setAmount(Money.of(req.amount()));
        e.setDescription(req.description());
        expenses.save(e);

        ledger.reverse(CostEntry.SourceType.EXPENSE, e.getId());
        ledger.post(new Posting(CostEntry.SourceType.EXPENSE, e.getId(), e.getExpenseDate(), e.location(),
                e.getWorkItemId(), e.getPartyId(), null, e.getAmount()));
        return e;
    }

    private ExpenseView view(SiteExpense e) {
        return new ExpenseView(e, e.getPartyId() != null ? support.party(e.getPartyId()).getName() : null, support.label(e));
    }

    private SiteExpense find(Long id) {
        return TenantGuard.own(expenses.findById(id), "SiteExpense", id);
    }
}
