package com.ccms.income;

import com.ccms.audit.AuditService;
import com.ccms.common.*;
import com.ccms.hierarchy.Client;
import com.ccms.hierarchy.HierarchyService;
import com.ccms.hierarchy.Site;
import com.ccms.income.IncomeRepositories.*;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * FR-5.3 Client bills (milestone, periodic or quantity-based running bills) and the money
 * collected against them. Income is what the client was billed; cash is what was received.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BillingService {

    private final ClientBillRepository bills;
    private final ClientBillLineRepository billLines;
    private final ClientReceiptRepository receipts;
    private final WorkOrderRepository workOrders;
    private final WorkOrderLineRepository woLines;
    private final MilestoneRepository milestones;
    private final IncomeSupport support;
    private final HierarchyService hierarchy;
    private final DocNumbers numbers;
    private final AuditService audit;

    public enum PayStatus { UNPAID, PARTIAL, PAID }

    public record BillRequest(@Size(max = 40) String billNo, @NotNull LocalDate billDate, LocalDate dueDate,
                              @NotNull Long workOrderId, Long milestoneId, LocalDate periodFrom, LocalDate periodTo,
                              @NotBlank @Size(max = 200) String title,
                              /* null = work order retention % of gross */
                              @PositiveOrZero @Digits(integer = 14, fraction = 2) BigDecimal retentionAmount,
                              @PositiveOrZero @Digits(integer = 14, fraction = 2) BigDecimal deductionAmount,
                              @Size(max = 200) String deductionNote, @Size(max = 1000) String notes,
                              @NotNull @Valid List<LineRequest> lines, boolean submit) {
    }

    public record ReceiptRequest(@NotNull LocalDate receiptDate, @NotNull Long workOrderId, Long billId,
                                 @NotNull @Positive @Digits(integer = 14, fraction = 2) BigDecimal amount,
                                 @NotNull PayMethod method, @Size(max = 100) String reference,
                                 @Size(max = 500) String note) {
    }

    public record BillSummary(Long id, String billNo, LocalDate billDate, LocalDate dueDate, String title,
                              ClientBill.Status status, Long workOrderId, String woNo, Long clientId, String clientName,
                              Long siteId, String siteName, Long milestoneId,
                              BigDecimal grossAmount, BigDecimal retentionAmount, BigDecimal deductionAmount,
                              BigDecimal netAmount, BigDecimal received, BigDecimal balance,
                              PayStatus payStatus, boolean overdue) {
    }

    public record BillView(@JsonUnwrapped ClientBill bill, String woNo, String woTitle, String clientRef,
                           BigDecimal contractValue, Client client, Site site, String milestoneTitle,
                           List<ClientBillLine> lines, List<ClientReceipt> receipts,
                           BigDecimal received, BigDecimal balance, PayStatus payStatus, boolean overdue,
                           /* submitted gross of this work order before this bill (running-bill print) */
                           BigDecimal previousGross) {
    }

    public record ReceiptView(@JsonUnwrapped ClientReceipt receipt, String clientName, String woNo, String billNo) {
    }

    // ------------------------------------------------------------------ bill queries

    @Transactional(readOnly = true)
    public List<BillSummary> list(Long workOrderId, Long clientId, ClientBill.Status status, LocalDate from, LocalDate to) {
        return summaries(bills.findAllByOrderByBillDateDescIdDesc().stream()
                .filter(b -> workOrderId == null || workOrderId.equals(b.getWorkOrderId()))
                .filter(b -> clientId == null || clientId.equals(b.getClientId()))
                .filter(b -> status == null || status == b.getStatus())
                .filter(b -> from == null || !b.getBillDate().isBefore(from))
                .filter(b -> to == null || !b.getBillDate().isAfter(to))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<BillSummary> summaries(List<ClientBill> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> received = new HashMap<>();
        for (Object[] r : receipts.receivedByBill(rows.stream().map(ClientBill::getId).toList())) {
            received.put((Long) r[0], (BigDecimal) r[1]);
        }
        Map<Long, String> clients = support.clientNames();
        Map<Long, String> sites = support.siteNames();
        Map<Long, String> woNos = new HashMap<>();
        workOrders.findAllById(rows.stream().map(ClientBill::getWorkOrderId).distinct().toList())
                .forEach(w -> woNos.put(w.getId(), w.getWoNo()));
        LocalDate today = LocalDate.now();
        return rows.stream().map(b -> {
            BigDecimal got = Money.of(received.get(b.getId()));
            BigDecimal balance = b.getStatus() == ClientBill.Status.SUBMITTED ? Money.of(b.getNetAmount().subtract(got)) : BigDecimal.ZERO;
            return new BillSummary(b.getId(), b.getBillNo(), b.getBillDate(), b.getDueDate(), b.getTitle(), b.getStatus(),
                    b.getWorkOrderId(), woNos.get(b.getWorkOrderId()), b.getClientId(), clients.get(b.getClientId()),
                    b.getSiteId(), sites.get(b.getSiteId()), b.getMilestoneId(),
                    b.getGrossAmount(), b.getRetentionAmount(), b.getDeductionAmount(), b.getNetAmount(), got, balance,
                    payStatus(b, got), overdue(b, balance, today));
        }).toList();
    }

    @Transactional(readOnly = true)
    public BillView view(Long id) {
        ClientBill b = find(id);
        WorkOrder wo = workOrders.findById(b.getWorkOrderId()).orElseThrow();
        List<ClientReceipt> got = receipts.findByBillIdOrderByReceiptDateAscIdAsc(id);
        BigDecimal received = Money.sum(got, ClientReceipt::getAmount);
        BigDecimal balance = b.getStatus() == ClientBill.Status.SUBMITTED ? Money.of(b.getNetAmount().subtract(received)) : BigDecimal.ZERO;
        BigDecimal previous = Money.sum(bills.findByWorkOrderIdOrderByBillDateAscIdAsc(b.getWorkOrderId()).stream()
                .filter(x -> x.getStatus() == ClientBill.Status.SUBMITTED && !x.getId().equals(id))
                .filter(x -> x.getBillDate().isBefore(b.getBillDate())
                        || (x.getBillDate().isEqual(b.getBillDate()) && x.getId() < id))
                .toList(), ClientBill::getGrossAmount);
        String milestone = b.getMilestoneId() != null
                ? milestones.findById(b.getMilestoneId()).map(BillingMilestone::getTitle).orElse(null) : null;
        return new BillView(b, wo.getWoNo(), wo.getTitle(), wo.getClientRef(), wo.getContractValue(),
                hierarchy.client(b.getClientId()), hierarchy.site(b.getSiteId()), milestone,
                billLines.findByBillIdOrderByLineNoAsc(id), got, received, balance, payStatus(b, received),
                overdue(b, balance, LocalDate.now()), previous);
    }

    // ------------------------------------------------------------------ bill commands

    public BillView create(BillRequest req) {
        WorkOrder wo = TenantGuard.own(workOrders.findById(req.workOrderId()), "WorkOrder", req.workOrderId());
        if (wo.getStatus() == WorkOrder.Status.CANCELLED) {
            throw IncomeSupport.invalidState("WO_CANCELLED", "Work order is cancelled");
        }
        ClientBill b = new ClientBill();
        b.setBillNo(numbers.orNext(req.billNo(), DocNumbers.Type.BILL, req.billDate()));
        b.setWorkOrderId(wo.getId());
        b.setClientId(wo.getClientId());
        b.setSiteId(wo.getSiteId());
        apply(b, wo, req);
        audit.log("BILL_CREATED", "BILL", b.getId(), b.getBillNo() + " " + b.getNetAmount());
        if (req.submit()) {
            submit(b.getId());
        }
        return view(b.getId());
    }

    public BillView update(Long id, BillRequest req) {
        ClientBill b = find(id);
        requireDraft(b);
        if (!b.getWorkOrderId().equals(req.workOrderId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Work order of a bill cannot change");
        }
        if (req.billNo() != null && !req.billNo().isBlank()) {
            b.setBillNo(req.billNo().trim());
        }
        apply(b, workOrders.findById(b.getWorkOrderId()).orElseThrow(), req);
        audit.log("BILL_UPDATED", "BILL", id, b.getBillNo() + " " + b.getNetAmount());
        if (req.submit()) {
            submit(id);
        }
        return view(id);
    }

    public BillView submit(Long id) {
        ClientBill b = find(id);
        requireDraft(b);
        if (b.getGrossAmount().signum() <= 0) {
            throw IncomeSupport.invalidState("BILL_EMPTY", "Bill has no amount");
        }
        b.setStatus(ClientBill.Status.SUBMITTED);
        audit.log("BILL_SUBMITTED", "BILL", id, b.getBillNo() + " " + b.getNetAmount());
        return view(id);
    }

    /** Back to draft for corrections; only while nothing has been received against it. */
    public BillView reopen(Long id) {
        ClientBill b = find(id);
        if (b.getStatus() != ClientBill.Status.SUBMITTED) {
            throw IncomeSupport.invalidState("BILL_NOT_SUBMITTED", "Only a submitted bill can be reopened");
        }
        requireNoReceipts(b);
        b.setStatus(ClientBill.Status.DRAFT);
        audit.log("BILL_REOPENED", "BILL", id, b.getBillNo());
        return view(id);
    }

    public BillView cancel(Long id) {
        ClientBill b = find(id);
        requireNoReceipts(b);
        b.setStatus(ClientBill.Status.CANCELLED);
        releaseMilestones(b.getId());
        b.setMilestoneId(null);
        audit.log("BILL_CANCELLED", "BILL", id, b.getBillNo());
        return view(id);
    }

    public void delete(Long id) {
        ClientBill b = find(id);
        if (b.getStatus() == ClientBill.Status.SUBMITTED) {
            throw IncomeSupport.invalidState("BILL_NOT_DRAFT", "Submitted bill cannot be deleted; cancel it instead");
        }
        requireNoReceipts(b);
        releaseMilestones(id);
        billLines.deleteByBillId(id);
        bills.delete(b);
        audit.log("BILL_DELETED", "BILL", id, b.getBillNo());
    }

    // ------------------------------------------------------------------ receipts

    @Transactional(readOnly = true)
    public List<ReceiptView> receipts(Long workOrderId, Long clientId, LocalDate from, LocalDate to) {
        List<ClientReceipt> rows = receipts.findAllByOrderByReceiptDateDescIdDesc().stream()
                .filter(r -> workOrderId == null || workOrderId.equals(r.getWorkOrderId()))
                .filter(r -> clientId == null || clientId.equals(r.getClientId()))
                .filter(r -> from == null || !r.getReceiptDate().isBefore(from))
                .filter(r -> to == null || !r.getReceiptDate().isAfter(to))
                .toList();
        Map<Long, String> clients = support.clientNames();
        Map<Long, String> woNos = new HashMap<>();
        workOrders.findAllById(rows.stream().map(ClientReceipt::getWorkOrderId).distinct().toList())
                .forEach(w -> woNos.put(w.getId(), w.getWoNo()));
        Map<Long, String> billNos = new HashMap<>();
        bills.findAllById(rows.stream().map(ClientReceipt::getBillId).filter(Objects::nonNull).distinct().toList())
                .forEach(b -> billNos.put(b.getId(), b.getBillNo()));
        return rows.stream().map(r -> new ReceiptView(r, clients.get(r.getClientId()), woNos.get(r.getWorkOrderId()),
                r.getBillId() != null ? billNos.get(r.getBillId()) : null)).toList();
    }

    public ClientReceipt receive(ReceiptRequest req) {
        WorkOrder wo = TenantGuard.own(workOrders.findById(req.workOrderId()), "WorkOrder", req.workOrderId());
        if (req.billId() != null) {
            ClientBill b = find(req.billId());
            if (!b.getWorkOrderId().equals(wo.getId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Bill belongs to another work order");
            }
            if (b.getStatus() != ClientBill.Status.SUBMITTED) {
                throw IncomeSupport.invalidState("BILL_NOT_SUBMITTED", "Bill is not submitted");
            }
        }
        ClientReceipt r = new ClientReceipt();
        r.setReceiptDate(req.receiptDate());
        r.setClientId(wo.getClientId());
        r.setWorkOrderId(wo.getId());
        r.setBillId(req.billId());
        r.setAmount(Money.of(req.amount()));
        r.setMethod(req.method());
        r.setReference(req.reference());
        r.setNote(req.note());
        receipts.save(r);
        audit.log("CLIENT_RECEIPT", "WORK_ORDER", wo.getId(), r.getAmount() + (req.billId() != null ? " bill " + req.billId() : " advance"));
        return r;
    }

    public void deleteReceipt(Long id) {
        ClientReceipt r = TenantGuard.own(receipts.findById(id), "ClientReceipt", id);
        receipts.delete(r);
        audit.log("CLIENT_RECEIPT_DELETED", "WORK_ORDER", r.getWorkOrderId(), r.getAmount().toString());
    }

    // ------------------------------------------------------------------ helpers

    private void apply(ClientBill b, WorkOrder wo, BillRequest req) {
        b.setBillDate(req.billDate());
        b.setDueDate(req.dueDate());
        b.setPeriodFrom(req.periodFrom());
        b.setPeriodTo(req.periodTo());
        b.setTitle(req.title().trim());
        b.setDeductionNote(req.deductionNote());
        b.setNotes(req.notes());
        bills.save(b);

        // Milestone link: release the old one, claim the new one.
        if (!Objects.equals(b.getMilestoneId(), req.milestoneId())) {
            releaseMilestones(b.getId());
            if (req.milestoneId() != null) {
                BillingMilestone m = TenantGuard.own(milestones.findById(req.milestoneId()), "Milestone", req.milestoneId());
                if (!m.getWorkOrderId().equals(wo.getId())) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "Milestone belongs to another work order");
                }
                if (m.getBillId() != null) {
                    throw IncomeSupport.invalidState("MILESTONE_BILLED", "Milestone already billed");
                }
                m.setBillId(b.getId());
            }
            b.setMilestoneId(req.milestoneId());
        }

        Map<Long, WorkOrderLine> woLineById = IncomeSupport.byId(woLines.findByWorkOrderIdOrderByLineNoAsc(wo.getId()), WorkOrderLine::getId);
        billLines.deleteByBillId(b.getId());
        List<ClientBillLine> saved = new ArrayList<>();
        List<LineRequest> reqLines = req.lines();
        if (reqLines.isEmpty() && req.milestoneId() != null) {
            BillingMilestone m = milestones.findById(req.milestoneId()).orElseThrow();
            reqLines = List.of(new LineRequest(null, null, null, m.getTitle(), "LS", BigDecimal.ONE, m.getAmount()));
        }
        for (int i = 0; i < reqLines.size(); i++) {
            LineRequest r = reqLines.get(i);
            Long workItemId = r.workItemId();
            if (r.workOrderLineId() != null) {
                WorkOrderLine wl = woLineById.get(r.workOrderLineId());
                if (wl == null) {
                    throw ApiException.notFound("WorkOrderLine", r.workOrderLineId());
                }
                if (workItemId == null) {
                    workItemId = wl.getWorkItemId();
                }
            }
            LineRequest effective = new LineRequest(null, workItemId, r.workOrderLineId(), r.description(), r.uom(), r.quantity(), r.rate());
            ClientBillLine l = support.fill(new ClientBillLine(), i + 1, effective);
            l.setBillId(b.getId());
            l.setWorkOrderLineId(r.workOrderLineId());
            saved.add(billLines.save(l));
        }

        BigDecimal gross = Money.sum(saved, ClientBillLine::getAmount);
        BigDecimal retention = req.retentionAmount() != null
                ? Money.of(req.retentionAmount())
                : gross.multiply(wo.getRetentionPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal deduction = Money.of(req.deductionAmount());
        BigDecimal net = gross.subtract(retention).subtract(deduction);
        if (net.signum() < 0) {
            throw IncomeSupport.invalidState("NEGATIVE_NET", "Deductions exceed the bill amount");
        }
        b.setGrossAmount(gross);
        b.setRetentionAmount(retention);
        b.setDeductionAmount(deduction);
        b.setNetAmount(Money.of(net));
    }

    private void releaseMilestones(Long billId) {
        milestones.findByBillId(billId).forEach(m -> m.setBillId(null));
    }

    private void requireDraft(ClientBill b) {
        if (b.getStatus() != ClientBill.Status.DRAFT) {
            throw IncomeSupport.invalidState("BILL_NOT_DRAFT", "Only a draft bill can be changed");
        }
    }

    private void requireNoReceipts(ClientBill b) {
        if (receipts.existsByBillId(b.getId())) {
            throw IncomeSupport.invalidState("BILL_HAS_RECEIPTS", "Payments were received against this bill");
        }
    }

    private static PayStatus payStatus(ClientBill b, BigDecimal received) {
        if (b.getStatus() != ClientBill.Status.SUBMITTED) {
            return null;
        }
        if (received.compareTo(b.getNetAmount()) >= 0) {
            return PayStatus.PAID;
        }
        return received.signum() > 0 ? PayStatus.PARTIAL : PayStatus.UNPAID;
    }

    private static boolean overdue(ClientBill b, BigDecimal balance, LocalDate today) {
        return b.getStatus() == ClientBill.Status.SUBMITTED && balance.signum() > 0
                && b.getDueDate() != null && b.getDueDate().isBefore(today);
    }

    private ClientBill find(Long id) {
        return TenantGuard.own(bills.findById(id), "ClientBill", id);
    }
}
