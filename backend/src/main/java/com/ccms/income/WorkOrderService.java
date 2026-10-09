package com.ccms.income;

import com.ccms.audit.AuditService;
import com.ccms.common.DocNumbers;
import com.ccms.common.Money;
import com.ccms.common.TenantGuard;
import com.ccms.income.BillingService.BillSummary;
import com.ccms.income.IncomeRepositories.*;
import com.ccms.income.QuotationController.ConvertRequest;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** FR-5.2 Work orders, their priced lines and the planned billing schedule. */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkOrderService {

    private final WorkOrderRepository workOrders;
    private final WorkOrderLineRepository lines;
    private final MilestoneRepository milestones;
    private final ClientBillRepository bills;
    private final ClientBillLineRepository billLines;
    private final ClientReceiptRepository receipts;
    private final QuotationRepository quotations;
    private final BillingService billing;
    private final IncomeSupport support;
    private final DocNumbers numbers;
    private final AuditService audit;

    public record WorkOrderRequest(@Size(max = 40) String woNo, @NotNull LocalDate woDate,
                                   @NotNull Long clientId, @NotNull Long siteId,
                                   @NotBlank @Size(max = 200) String title, @Size(max = 100) String clientRef,
                                   LocalDate startDate, LocalDate endDate,
                                   @PositiveOrZero @Max(100) BigDecimal retentionPercent,
                                   @PositiveOrZero @Digits(integer = 14, fraction = 2) BigDecimal discount,
                                   /* used only when there are no lines (lump-sum contract) */
                                   @PositiveOrZero @Digits(integer = 14, fraction = 2) BigDecimal contractValue,
                                   @Size(max = 1000) String notes,
                                   @NotNull @Valid List<LineRequest> lines) {
    }

    public record MilestoneRequest(Long id, @NotBlank @Size(max = 200) String title, LocalDate dueDate,
                                   @NotNull @PositiveOrZero @Digits(integer = 14, fraction = 2) BigDecimal amount) {
    }

    /** Money position of one work order. receivable < 0 means the client paid in advance. */
    public record Totals(BigDecimal contractValue, BigDecimal billedGross, BigDecimal retentionHeld,
                         BigDecimal deductions, BigDecimal billedNet, BigDecimal received,
                         BigDecimal receivable, BigDecimal unbilled) {

        static Totals of(BigDecimal contract, BigDecimal gross, BigDecimal retention, BigDecimal deduction,
                         BigDecimal net, BigDecimal received) {
            return new Totals(Money.of(contract), Money.of(gross), Money.of(retention), Money.of(deduction),
                    Money.of(net), Money.of(received), Money.of(net.subtract(received)),
                    Money.of(contract.subtract(gross)));
        }
    }

    public record LineView(@JsonUnwrapped WorkOrderLine line, BigDecimal billedQty, BigDecimal billedAmount) {
    }

    public record MilestoneView(@JsonUnwrapped BillingMilestone milestone, String billNo, boolean due) {
    }

    public record WorkOrderSummary(@JsonUnwrapped WorkOrder workOrder, String clientName, String siteName,
                                   Totals totals, BillingMilestone nextMilestone, long dueMilestones) {
    }

    public record WorkOrderView(@JsonUnwrapped WorkOrder workOrder, String clientName, String siteName,
                                String quotationNo, Totals totals, List<LineView> lines,
                                List<MilestoneView> milestones, List<BillSummary> bills,
                                List<ClientReceipt> receipts) {
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<WorkOrderSummary> list(Long clientId, Long siteId, WorkOrder.Status status) {
        Map<Long, String> clients = support.clientNames();
        Map<Long, String> sites = support.siteNames();
        Map<Long, Object[]> billed = new HashMap<>();
        for (Object[] r : bills.totalsByWorkOrder()) billed.put((Long) r[0], r);
        Map<Long, BigDecimal> received = new HashMap<>();
        for (Object[] r : receipts.receivedByWorkOrder()) received.put((Long) r[0], (BigDecimal) r[1]);
        Map<Long, List<BillingMilestone>> plan = milestones.findAll().stream()
                .sorted(Comparator.comparingInt(BillingMilestone::getSeq))
                .collect(Collectors.groupingBy(BillingMilestone::getWorkOrderId));
        LocalDate today = LocalDate.now();

        return workOrders.findAllByOrderByWoDateDescIdDesc().stream()
                .filter(w -> clientId == null || clientId.equals(w.getClientId()))
                .filter(w -> siteId == null || siteId.equals(w.getSiteId()))
                .filter(w -> status == null || status == w.getStatus())
                .map(w -> {
                    Object[] b = billed.get(w.getId());
                    Totals t = Totals.of(w.getContractValue(),
                            b != null ? (BigDecimal) b[1] : BigDecimal.ZERO, b != null ? (BigDecimal) b[2] : BigDecimal.ZERO,
                            b != null ? (BigDecimal) b[3] : BigDecimal.ZERO, b != null ? (BigDecimal) b[4] : BigDecimal.ZERO,
                            received.getOrDefault(w.getId(), BigDecimal.ZERO));
                    List<BillingMilestone> open = plan.getOrDefault(w.getId(), List.of()).stream()
                            .filter(m -> m.getBillId() == null).toList();
                    long due = open.stream().filter(m -> isDue(m, today)).count();
                    return new WorkOrderSummary(w, clients.get(w.getClientId()), sites.get(w.getSiteId()), t,
                            open.isEmpty() ? null : open.get(0), due);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkOrderView view(Long id) {
        return view(find(id));
    }

    public WorkOrder find(Long id) {
        return TenantGuard.own(workOrders.findById(id), "WorkOrder", id);
    }

    // ------------------------------------------------------------------ commands

    public WorkOrderView create(WorkOrderRequest req) {
        WorkOrder w = new WorkOrder();
        w.setWoNo(numbers.orNext(req.woNo(), DocNumbers.Type.WORK_ORDER, req.woDate()));
        WorkOrderView v = save(w, req);
        audit.log("WORK_ORDER_CREATED", "WORK_ORDER", w.getId(), w.getWoNo() + " " + w.getContractValue());
        return v;
    }

    public WorkOrderView update(Long id, WorkOrderRequest req) {
        WorkOrder w = find(id);
        if (req.woNo() != null && !req.woNo().isBlank()) {
            w.setWoNo(req.woNo().trim());
        }
        WorkOrderView v = save(w, req);
        audit.log("WORK_ORDER_UPDATED", "WORK_ORDER", id, w.getWoNo() + " " + w.getContractValue());
        return v;
    }

    WorkOrderView createFromQuotation(Quotation q, List<QuotationLine> qLines, ConvertRequest req) {
        Long siteId = req.siteId() != null ? req.siteId() : q.getSiteId();
        if (siteId == null) {
            throw new com.ccms.common.ApiException(com.ccms.common.ErrorCode.INVALID_LOCATION, "Site is required");
        }
        List<LineRequest> copied = qLines.stream()
                .map(l -> new LineRequest(null, l.getWorkItemId(), null, l.getDescription(), l.getUom(), l.getQuantity(), l.getRate()))
                .toList();
        WorkOrderRequest wr = new WorkOrderRequest(req.woNo(), req.woDate() != null ? req.woDate() : LocalDate.now(),
                q.getClientId(), siteId, q.getTitle(), req.clientRef(), req.startDate(), req.endDate(),
                req.retentionPercent(), q.getDiscount(), q.getTotal(), q.getNotes(), copied);
        WorkOrder w = new WorkOrder();
        w.setQuotationId(q.getId());
        w.setWoNo(numbers.orNext(req.woNo(), DocNumbers.Type.WORK_ORDER, wr.woDate()));
        WorkOrderView v = save(w, wr);
        audit.log("WORK_ORDER_CREATED", "WORK_ORDER", w.getId(), w.getWoNo() + " from quotation " + q.getQuoteNo());
        return v;
    }

    public WorkOrderView setStatus(Long id, WorkOrder.Status status) {
        WorkOrder w = find(id);
        w.setStatus(status);
        audit.log("WORK_ORDER_STATUS", "WORK_ORDER", id, status.name());
        return view(w);
    }

    /**
     * Replaces the billing plan. Milestones that are already billed are kept as they are;
     * the rest follow the request (update by id, add new, drop missing).
     */
    public WorkOrderView replaceMilestones(Long id, List<MilestoneRequest> req) {
        WorkOrder w = find(id);
        Map<Long, BillingMilestone> existing = IncomeSupport.byId(milestones.findByWorkOrderIdOrderBySeqAsc(id), BillingMilestone::getId);
        Set<Long> kept = new HashSet<>();
        int seq = 1;
        for (MilestoneRequest r : req) {
            if (r == null || r.title() == null || r.title().isBlank() || r.amount() == null || r.amount().signum() < 0) {
                throw new com.ccms.common.ApiException(com.ccms.common.ErrorCode.VALIDATION_FAILED, "Milestone title and amount are required");
            }
            BillingMilestone m;
            if (r.id() != null) {
                m = existing.get(r.id());
                if (m == null) {
                    throw com.ccms.common.ApiException.notFound("Milestone", r.id());
                }
                kept.add(m.getId());
                if (m.getBillId() != null) {
                    m.setSeq(seq++);
                    continue;
                }
            } else {
                m = new BillingMilestone();
                m.setWorkOrderId(id);
            }
            m.setSeq(seq++);
            m.setTitle(r.title().trim());
            m.setDueDate(r.dueDate());
            m.setAmount(Money.of(r.amount()));
            milestones.save(m);
        }
        for (BillingMilestone m : existing.values()) {
            if (kept.contains(m.getId())) continue;
            if (m.getBillId() != null) {
                m.setSeq(seq++);
            } else {
                milestones.delete(m);
            }
        }
        audit.log("WORK_ORDER_MILESTONES", "WORK_ORDER", id, req.size() + " milestones");
        return view(w);
    }

    public void delete(Long id) {
        WorkOrder w = find(id);
        if (bills.existsByWorkOrderId(id) || receipts.existsByWorkOrderId(id)) {
            throw IncomeSupport.invalidState("WO_HAS_BILLS", "Work order has bills or receipts");
        }
        milestones.deleteAll(milestones.findByWorkOrderIdOrderBySeqAsc(id));
        lines.deleteAll(lines.findByWorkOrderIdOrderByLineNoAsc(id));
        if (w.getQuotationId() != null) {
            quotations.findById(w.getQuotationId()).ifPresent(q -> q.setStatus(Quotation.Status.SENT));
        }
        workOrders.delete(w);
        audit.log("WORK_ORDER_DELETED", "WORK_ORDER", id, w.getWoNo());
    }

    // ------------------------------------------------------------------ helpers

    private WorkOrderView save(WorkOrder w, WorkOrderRequest req) {
        support.checkClientSite(req.clientId(), req.siteId());
        w.setWoDate(req.woDate());
        w.setClientId(req.clientId());
        w.setSiteId(req.siteId());
        w.setTitle(req.title().trim());
        w.setClientRef(req.clientRef());
        w.setStartDate(req.startDate());
        w.setEndDate(req.endDate());
        w.setRetentionPercent(req.retentionPercent() != null ? req.retentionPercent() : BigDecimal.ZERO);
        w.setDiscount(Money.of(req.discount()));
        w.setNotes(req.notes());
        workOrders.save(w);

        Map<Long, WorkOrderLine> existing = IncomeSupport.byId(lines.findByWorkOrderIdOrderByLineNoAsc(w.getId()), WorkOrderLine::getId);
        Set<Long> kept = new HashSet<>();
        List<WorkOrderLine> saved = new ArrayList<>();
        for (int i = 0; i < req.lines().size(); i++) {
            LineRequest r = req.lines().get(i);
            WorkOrderLine l;
            if (r.id() != null) {
                l = existing.get(r.id());
                if (l == null) {
                    throw com.ccms.common.ApiException.notFound("WorkOrderLine", r.id());
                }
                kept.add(l.getId());
            } else {
                l = new WorkOrderLine();
                l.setWorkOrderId(w.getId());
            }
            saved.add(lines.save(support.fill(l, i + 1, r)));
        }
        for (WorkOrderLine l : existing.values()) {
            if (kept.contains(l.getId())) continue;
            if (billLines.existsByWorkOrderLineId(l.getId())) {
                throw IncomeSupport.invalidState("LINE_BILLED", "Line already billed: " + l.getDescription());
            }
            lines.delete(l);
        }
        w.setContractValue(saved.isEmpty() ? Money.of(req.contractValue())
                : Money.sum(saved, WorkOrderLine::getAmount).subtract(w.getDiscount()).max(BigDecimal.ZERO));
        return view(w);
    }

    private WorkOrderView view(WorkOrder w) {
        Map<Long, BigDecimal[]> billed = new HashMap<>();
        for (Object[] r : billLines.billedByLine(w.getId())) {
            billed.put((Long) r[0], new BigDecimal[]{(BigDecimal) r[1], (BigDecimal) r[2]});
        }
        List<LineView> lineViews = lines.findByWorkOrderIdOrderByLineNoAsc(w.getId()).stream()
                .map(l -> {
                    BigDecimal[] b = billed.getOrDefault(l.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    return new LineView(l, b[0], b[1]);
                })
                .toList();

        List<BillSummary> billList = billing.summaries(bills.findByWorkOrderIdOrderByBillDateAscIdAsc(w.getId()));
        Map<Long, String> billNos = billList.stream().collect(Collectors.toMap(BillSummary::id, BillSummary::billNo));
        LocalDate today = LocalDate.now();
        List<MilestoneView> plan = milestones.findByWorkOrderIdOrderBySeqAsc(w.getId()).stream()
                .map(m -> new MilestoneView(m, m.getBillId() != null ? billNos.get(m.getBillId()) : null, isDue(m, today)))
                .toList();
        List<ClientReceipt> got = receipts.findByWorkOrderIdOrderByReceiptDateAscIdAsc(w.getId());

        List<BillSummary> submitted = billList.stream().filter(b -> b.status() == ClientBill.Status.SUBMITTED).toList();
        Totals totals = Totals.of(w.getContractValue(),
                Money.sum(submitted, BillSummary::grossAmount), Money.sum(submitted, BillSummary::retentionAmount),
                Money.sum(submitted, BillSummary::deductionAmount), Money.sum(submitted, BillSummary::netAmount),
                Money.sum(got, ClientReceipt::getAmount));

        String quoteNo = w.getQuotationId() != null
                ? quotations.findById(w.getQuotationId()).map(Quotation::getQuoteNo).orElse(null) : null;
        return new WorkOrderView(w, support.clientNames().get(w.getClientId()), support.siteNames().get(w.getSiteId()),
                quoteNo, totals, lineViews, plan, billList, got);
    }

    private static boolean isDue(BillingMilestone m, LocalDate today) {
        return m.getBillId() == null && m.getDueDate() != null && !m.getDueDate().isAfter(today);
    }
}
