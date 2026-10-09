package com.ccms.expense;

import com.ccms.audit.AuditService;
import com.ccms.common.Money;
import com.ccms.common.PayMethod;
import com.ccms.common.TenantGuard;
import com.ccms.costing.CostEntry;
import com.ccms.costing.LedgerPoster;
import com.ccms.costing.LedgerPoster.Posting;
import com.ccms.expense.ExpenseRepositories.*;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** FR-4 Material purchases. Each line is posted to the cost ledger with its material. */
@RestController
@RequestMapping("/api/app/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseRepository purchases;
    private final PurchaseLineRepository lines;
    private final PartyPaymentRepository payments;
    private final ExpenseSupport support;
    private final LedgerPoster ledger;
    private final AuditService audit;

    public record PurchaseLineRequest(Long materialId, @Size(max = 300) String description, @Size(max = 20) String uom,
                                      @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity,
                                      @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal rate) {
    }

    public record PurchaseRequest(@NotNull LocalDate purchaseDate, Long partyId, @Size(max = 60) String invoiceNo,
                                  @NotNull Long siteId, Long buildingId, Long floorId, Long unitId, Long workItemId,
                                  /* party purchases: amount paid on the spot */
                                  @PositiveOrZero @Digits(integer = 14, fraction = 2) BigDecimal paidAmount,
                                  PayMethod payMethod, @Size(max = 500) String note,
                                  @NotEmpty @Valid List<PurchaseLineRequest> lines) {
    }

    public record PurchaseView(@JsonUnwrapped Purchase purchase, String partyName, String locationLabel,
                               List<PurchaseLine> lines) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PurchaseView> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) Long siteId,
                                   @RequestParam(required = false) Long partyId) {
        LocalDate end = to != null ? to : LocalDate.now();
        List<Purchase> rows = purchases.between(from != null ? from : end.minusDays(30), end).stream()
                .filter(p -> siteId == null || siteId.equals(p.getSiteId()))
                .filter(p -> partyId == null || partyId.equals(p.getPartyId()))
                .toList();
        Map<Long, List<PurchaseLine>> byPurchase = rows.isEmpty() ? Map.of()
                : lines.findByPurchaseIdIn(rows.stream().map(Purchase::getId).toList()).stream()
                .collect(Collectors.groupingBy(PurchaseLine::getPurchaseId));
        Map<Long, String> parties = support.partyNames();
        return rows.stream().map(p -> new PurchaseView(p, parties.get(p.getPartyId()), support.label(p),
                byPurchase.getOrDefault(p.getId(), List.of()))).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public PurchaseView get(@PathVariable Long id) {
        return view(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PurchaseView create(@Valid @RequestBody PurchaseRequest req) {
        Purchase p = save(new Purchase(), req);
        audit.log("PURCHASE_CREATED", "PURCHASE", p.getId(), p.getTotalAmount().toString());
        return view(p);
    }

    @PutMapping("/{id}")
    @Transactional
    public PurchaseView update(@PathVariable Long id, @Valid @RequestBody PurchaseRequest req) {
        Purchase p = save(find(id), req);
        audit.log("PURCHASE_UPDATED", "PURCHASE", id, p.getTotalAmount().toString());
        return view(p);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        Purchase p = find(id);
        ledger.reverse(CostEntry.SourceType.MATERIAL, id);
        payments.deleteAll(payments.findByPurchaseId(id));
        lines.deleteByPurchaseId(id);
        purchases.delete(p);
        audit.log("PURCHASE_DELETED", "PURCHASE", id, p.getTotalAmount().toString());
    }

    private Purchase save(Purchase p, PurchaseRequest req) {
        if (req.partyId() != null) {
            support.party(req.partyId());
        }
        support.place(p, new Place(req.siteId(), req.buildingId(), req.floorId(), req.unitId(), req.workItemId()));
        p.setPurchaseDate(req.purchaseDate());
        p.setPartyId(req.partyId());
        p.setInvoiceNo(req.invoiceNo());
        p.setNote(req.note());
        purchases.save(p);

        lines.deleteByPurchaseId(p.getId());
        List<PurchaseLine> saved = new ArrayList<>();
        for (PurchaseLineRequest r : req.lines()) {
            support.checkMaterial(r.materialId());
            PurchaseLine l = new PurchaseLine();
            l.setPurchaseId(p.getId());
            l.setMaterialId(r.materialId());
            l.setDescription(r.description());
            l.setUom(r.uom() == null || r.uom().isBlank() ? null : r.uom().trim().toUpperCase());
            l.setQuantity(r.quantity());
            l.setRate(r.rate());
            l.setAmount(Money.times(r.quantity(), r.rate()));
            saved.add(lines.save(l));
        }
        p.setTotalAmount(Money.sum(saved, PurchaseLine::getAmount));

        // Cash purchase: paid in full. Party purchase: optional on-the-spot payment, rest is payable.
        payments.deleteAll(payments.findByPurchaseId(p.getId()));
        if (p.getPartyId() == null) {
            p.setPaidAmount(p.getTotalAmount());
        } else {
            BigDecimal paid = Money.of(req.paidAmount());
            p.setPaidAmount(paid);
            if (paid.signum() > 0) {
                PartyPayment pay = new PartyPayment();
                pay.setPartyId(p.getPartyId());
                pay.setPayDate(p.getPurchaseDate());
                pay.setAmount(paid);
                pay.setMethod(req.payMethod() != null ? req.payMethod() : PayMethod.CASH);
                pay.setPurchaseId(p.getId());
                pay.setNote(p.getInvoiceNo());
                payments.save(pay);
            }
        }

        ledger.reverse(CostEntry.SourceType.MATERIAL, p.getId());
        for (PurchaseLine l : saved) {
            ledger.post(new Posting(CostEntry.SourceType.MATERIAL, p.getId(), p.getPurchaseDate(), p.location(),
                    p.getWorkItemId(), p.getPartyId(), l.getMaterialId(), l.getAmount()));
        }
        return p;
    }

    private PurchaseView view(Purchase p) {
        return new PurchaseView(p, p.getPartyId() != null ? support.party(p.getPartyId()).getName() : null,
                support.label(p), lines.findByPurchaseIdOrderByIdAsc(p.getId()));
    }

    private Purchase find(Long id) {
        return TenantGuard.own(purchases.findById(id), "Purchase", id);
    }
}
