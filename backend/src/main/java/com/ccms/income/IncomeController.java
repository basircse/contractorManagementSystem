package com.ccms.income;

import com.ccms.income.BillingService.*;
import com.ccms.income.WorkOrderService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Work orders, client bills and receipts. Every GET is open to the admin (read-only view). */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class IncomeController {

    private final WorkOrderService workOrders;
    private final BillingService billing;

    public record WorkOrderStatusRequest(@NotNull WorkOrder.Status status) {
    }

    // ------------------------------------------------------------------ work orders

    @GetMapping("/work-orders")
    public List<WorkOrderSummary> workOrders(@RequestParam(required = false) Long clientId,
                                             @RequestParam(required = false) Long siteId,
                                             @RequestParam(required = false) WorkOrder.Status status) {
        return workOrders.list(clientId, siteId, status);
    }

    @GetMapping("/work-orders/{id}")
    public WorkOrderView workOrder(@PathVariable Long id) {
        return workOrders.view(id);
    }

    @PostMapping("/work-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrderView createWorkOrder(@Valid @RequestBody WorkOrderRequest req) {
        return workOrders.create(req);
    }

    @PutMapping("/work-orders/{id}")
    public WorkOrderView updateWorkOrder(@PathVariable Long id, @Valid @RequestBody WorkOrderRequest req) {
        return workOrders.update(id, req);
    }

    @PostMapping("/work-orders/{id}/status")
    public WorkOrderView workOrderStatus(@PathVariable Long id, @Valid @RequestBody WorkOrderStatusRequest req) {
        return workOrders.setStatus(id, req.status());
    }

    @PutMapping("/work-orders/{id}/milestones")
    public WorkOrderView milestones(@PathVariable Long id, @Valid @RequestBody List<@Valid MilestoneRequest> req) {
        return workOrders.replaceMilestones(id, req);
    }

    @DeleteMapping("/work-orders/{id}")
    public void deleteWorkOrder(@PathVariable Long id) {
        workOrders.delete(id);
    }

    // ------------------------------------------------------------------ bills

    @GetMapping("/bills")
    public List<BillSummary> bills(@RequestParam(required = false) Long workOrderId,
                                   @RequestParam(required = false) Long clientId,
                                   @RequestParam(required = false) ClientBill.Status status,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return billing.list(workOrderId, clientId, status, from, to);
    }

    @GetMapping("/bills/{id}")
    public BillView bill(@PathVariable Long id) {
        return billing.view(id);
    }

    @PostMapping("/bills")
    @ResponseStatus(HttpStatus.CREATED)
    public BillView createBill(@Valid @RequestBody BillRequest req) {
        return billing.create(req);
    }

    @PutMapping("/bills/{id}")
    public BillView updateBill(@PathVariable Long id, @Valid @RequestBody BillRequest req) {
        return billing.update(id, req);
    }

    @PostMapping("/bills/{id}/submit")
    public BillView submitBill(@PathVariable Long id) {
        return billing.submit(id);
    }

    @PostMapping("/bills/{id}/reopen")
    public BillView reopenBill(@PathVariable Long id) {
        return billing.reopen(id);
    }

    @PostMapping("/bills/{id}/cancel")
    public BillView cancelBill(@PathVariable Long id) {
        return billing.cancel(id);
    }

    @DeleteMapping("/bills/{id}")
    public void deleteBill(@PathVariable Long id) {
        billing.delete(id);
    }

    // ------------------------------------------------------------------ receipts

    @GetMapping("/receipts")
    public List<ReceiptView> receipts(@RequestParam(required = false) Long workOrderId,
                                      @RequestParam(required = false) Long clientId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return billing.receipts(workOrderId, clientId, from, to);
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientReceipt receive(@Valid @RequestBody ReceiptRequest req) {
        return billing.receive(req);
    }

    @DeleteMapping("/receipts/{id}")
    public void deleteReceipt(@PathVariable Long id) {
        billing.deleteReceipt(id);
    }
}
