package com.ccms.income;

import com.ccms.audit.AuditService;
import com.ccms.common.DocNumbers;
import com.ccms.common.Money;
import com.ccms.common.TenantGuard;
import com.ccms.income.IncomeRepositories.QuotationLineRepository;
import com.ccms.income.IncomeRepositories.QuotationRepository;
import com.ccms.income.IncomeRepositories.WorkOrderRepository;
import com.ccms.income.WorkOrderService.WorkOrderView;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** FR-5.1 Quotations / BOQ. */
@RestController
@RequestMapping("/api/app/quotations")
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationRepository quotations;
    private final QuotationLineRepository lines;
    private final WorkOrderRepository workOrders;
    private final WorkOrderService workOrderService;
    private final IncomeSupport support;
    private final DocNumbers numbers;
    private final AuditService audit;

    public record QuotationRequest(@Size(max = 40) String quoteNo, @NotNull LocalDate quoteDate, LocalDate validUntil,
                                   @NotNull Long clientId, Long siteId, @NotBlank @Size(max = 200) String title,
                                   @PositiveOrZero @Digits(integer = 14, fraction = 2) BigDecimal discount,
                                   @Size(max = 5000) String terms, @Size(max = 1000) String notes,
                                   @NotNull @Valid List<LineRequest> lines) {
    }

    public record StatusRequest(@NotNull Quotation.Status status) {
    }

    public record ConvertRequest(Long siteId, LocalDate woDate, @Size(max = 40) String woNo,
                                 @Size(max = 100) String clientRef, LocalDate startDate, LocalDate endDate,
                                 @PositiveOrZero @Max(100) BigDecimal retentionPercent) {
    }

    public record QuotationView(@JsonUnwrapped Quotation quotation, String clientName, String siteName,
                                Long workOrderId, String workOrderNo, List<QuotationLine> lines) {
    }

    @GetMapping
    public List<QuotationView> list(@RequestParam(required = false) Long clientId,
                                    @RequestParam(required = false) Quotation.Status status) {
        Map<Long, String> clients = support.clientNames();
        Map<Long, String> sites = support.siteNames();
        Map<Long, WorkOrder> woByQuote = new java.util.HashMap<>();
        workOrders.findAll().stream().filter(w -> w.getQuotationId() != null)
                .forEach(w -> woByQuote.putIfAbsent(w.getQuotationId(), w));
        return quotations.findAllByOrderByQuoteDateDescIdDesc().stream()
                .filter(q -> clientId == null || clientId.equals(q.getClientId()))
                .filter(q -> status == null || status == q.getStatus())
                .map(q -> {
                    WorkOrder wo = woByQuote.get(q.getId());
                    return new QuotationView(q, clients.get(q.getClientId()), sites.get(q.getSiteId()),
                            wo != null ? wo.getId() : null, wo != null ? wo.getWoNo() : null, null);
                })
                .toList();
    }

    @GetMapping("/{id}")
    public QuotationView get(@PathVariable Long id) {
        return view(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public QuotationView create(@Valid @RequestBody QuotationRequest req) {
        Quotation q = new Quotation();
        q.setQuoteNo(numbers.orNext(req.quoteNo(), DocNumbers.Type.QUOTATION, req.quoteDate()));
        QuotationView v = save(q, req);
        audit.log("QUOTATION_CREATED", "QUOTATION", q.getId(), q.getQuoteNo() + " " + q.getTotal());
        return v;
    }

    @PutMapping("/{id}")
    @Transactional
    public QuotationView update(@PathVariable Long id, @Valid @RequestBody QuotationRequest req) {
        Quotation q = find(id);
        requireEditable(q);
        if (req.quoteNo() != null && !req.quoteNo().isBlank()) {
            q.setQuoteNo(req.quoteNo().trim());
        }
        return save(q, req);
    }

    @PostMapping("/{id}/status")
    @Transactional
    public QuotationView setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        Quotation q = find(id);
        if (workOrders.findFirstByQuotationId(id).isPresent()) {
            throw IncomeSupport.invalidState("HAS_WORK_ORDER", "Quotation already converted to a work order");
        }
        q.setStatus(req.status());
        audit.log("QUOTATION_STATUS", "QUOTATION", id, req.status().name());
        return view(q);
    }

    /** Revisions: copy into a new draft with a new number. */
    @PostMapping("/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public QuotationView copy(@PathVariable Long id) {
        Quotation src = find(id);
        Quotation q = new Quotation();
        q.setQuoteNo(numbers.next(DocNumbers.Type.QUOTATION, LocalDate.now()));
        q.setQuoteDate(LocalDate.now());
        q.setValidUntil(src.getValidUntil());
        q.setClientId(src.getClientId());
        q.setSiteId(src.getSiteId());
        q.setTitle(src.getTitle());
        q.setDiscount(src.getDiscount());
        q.setSubtotal(src.getSubtotal());
        q.setTotal(src.getTotal());
        q.setTerms(src.getTerms());
        q.setNotes(src.getNotes());
        quotations.save(q);
        for (QuotationLine s : lines.findByQuotationIdOrderByLineNoAsc(id)) {
            QuotationLine l = new QuotationLine();
            l.setQuotationId(q.getId());
            l.setLineNo(s.getLineNo());
            l.setWorkItemId(s.getWorkItemId());
            l.setDescription(s.getDescription());
            l.setUom(s.getUom());
            l.setQuantity(s.getQuantity());
            l.setRate(s.getRate());
            l.setAmount(s.getAmount());
            lines.save(l);
        }
        audit.log("QUOTATION_COPIED", "QUOTATION", q.getId(), "from " + src.getQuoteNo());
        return view(q);
    }

    /** Client accepted: create the work order from the quotation lines. */
    @PostMapping("/{id}/work-order")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public WorkOrderView convert(@PathVariable Long id, @Valid @RequestBody ConvertRequest req) {
        Quotation q = find(id);
        if (workOrders.findFirstByQuotationId(id).isPresent()) {
            throw IncomeSupport.invalidState("HAS_WORK_ORDER", "Quotation already converted to a work order");
        }
        WorkOrderView wo = workOrderService.createFromQuotation(q, lines.findByQuotationIdOrderByLineNoAsc(id), req);
        q.setStatus(Quotation.Status.ACCEPTED);
        audit.log("QUOTATION_ACCEPTED", "QUOTATION", id, "work order " + wo.workOrder().getWoNo());
        return wo;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        Quotation q = find(id);
        if (workOrders.findFirstByQuotationId(id).isPresent()) {
            throw IncomeSupport.invalidState("HAS_WORK_ORDER", "Quotation already converted to a work order");
        }
        lines.deleteByQuotationId(id);
        quotations.delete(q);
        audit.log("QUOTATION_DELETED", "QUOTATION", id, q.getQuoteNo());
    }

    // ------------------------------------------------------------------ helpers

    private QuotationView save(Quotation q, QuotationRequest req) {
        support.checkClientSite(req.clientId(), req.siteId());
        q.setQuoteDate(req.quoteDate());
        q.setValidUntil(req.validUntil());
        q.setClientId(req.clientId());
        q.setSiteId(req.siteId());
        q.setTitle(req.title().trim());
        q.setTerms(req.terms());
        q.setNotes(req.notes());
        q.setDiscount(Money.of(req.discount()));
        quotations.save(q);

        lines.deleteByQuotationId(q.getId());
        List<QuotationLine> saved = new ArrayList<>();
        for (int i = 0; i < req.lines().size(); i++) {
            QuotationLine l = support.fill(new QuotationLine(), i + 1, req.lines().get(i));
            l.setQuotationId(q.getId());
            saved.add(lines.save(l));
        }
        q.setSubtotal(Money.sum(saved, QuotationLine::getAmount));
        q.setTotal(q.getSubtotal().subtract(q.getDiscount()).max(BigDecimal.ZERO));
        return view(q);
    }

    private QuotationView view(Quotation q) {
        WorkOrder wo = workOrders.findFirstByQuotationId(q.getId()).orElse(null);
        Map<Long, String> sites = support.siteNames();
        return new QuotationView(q, support.clientNames().get(q.getClientId()),
                q.getSiteId() != null ? sites.get(q.getSiteId()) : null,
                wo != null ? wo.getId() : null, wo != null ? wo.getWoNo() : null,
                lines.findByQuotationIdOrderByLineNoAsc(q.getId()));
    }

    private void requireEditable(Quotation q) {
        if (q.getStatus() == Quotation.Status.ACCEPTED && workOrders.findFirstByQuotationId(q.getId()).isPresent()) {
            throw IncomeSupport.invalidState("QUOTE_LOCKED", "Accepted quotation cannot be edited");
        }
    }

    private Quotation find(Long id) {
        return TenantGuard.own(quotations.findById(id), "Quotation", id);
    }
}
