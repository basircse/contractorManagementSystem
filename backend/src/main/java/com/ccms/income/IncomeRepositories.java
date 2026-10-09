package com.ccms.income;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class IncomeRepositories {

    private IncomeRepositories() {
    }

    public interface QuotationRepository extends JpaRepository<Quotation, Long> {
        List<Quotation> findAllByOrderByQuoteDateDescIdDesc();
    }

    public interface QuotationLineRepository extends JpaRepository<QuotationLine, Long> {
        List<QuotationLine> findByQuotationIdOrderByLineNoAsc(Long quotationId);

        @Modifying
        @Query("delete from QuotationLine l where l.quotationId = :quotationId")
        void deleteByQuotationId(Long quotationId);
    }

    public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
        List<WorkOrder> findAllByOrderByWoDateDescIdDesc();

        Optional<WorkOrder> findFirstByQuotationId(Long quotationId);
    }

    public interface WorkOrderLineRepository extends JpaRepository<WorkOrderLine, Long> {
        List<WorkOrderLine> findByWorkOrderIdOrderByLineNoAsc(Long workOrderId);
    }

    public interface MilestoneRepository extends JpaRepository<BillingMilestone, Long> {
        List<BillingMilestone> findByWorkOrderIdOrderBySeqAsc(Long workOrderId);

        List<BillingMilestone> findByBillId(Long billId);
    }

    public interface ClientBillRepository extends JpaRepository<ClientBill, Long> {
        List<ClientBill> findAllByOrderByBillDateDescIdDesc();

        List<ClientBill> findByWorkOrderIdOrderByBillDateAscIdAsc(Long workOrderId);

        boolean existsByWorkOrderId(Long workOrderId);

        /** [workOrderId, gross, retention, deduction, net] over submitted bills. */
        @Query("""
                select b.workOrderId, sum(b.grossAmount), sum(b.retentionAmount), sum(b.deductionAmount), sum(b.netAmount)
                from ClientBill b where b.status = com.ccms.income.ClientBill.Status.SUBMITTED
                group by b.workOrderId""")
        List<Object[]> totalsByWorkOrder();
    }

    public interface ClientBillLineRepository extends JpaRepository<ClientBillLine, Long> {
        List<ClientBillLine> findByBillIdOrderByLineNoAsc(Long billId);

        @Modifying
        @Query("delete from ClientBillLine l where l.billId = :billId")
        void deleteByBillId(Long billId);

        boolean existsByWorkOrderLineId(Long workOrderLineId);

        /** Quantity and amount billed so far per work-order line (submitted bills only). */
        @Query("""
                select l.workOrderLineId, sum(l.quantity), sum(l.amount) from ClientBillLine l, ClientBill b
                where b.id = l.billId and b.workOrderId = :workOrderId and l.workOrderLineId is not null
                  and b.status = com.ccms.income.ClientBill.Status.SUBMITTED
                group by l.workOrderLineId""")
        List<Object[]> billedByLine(Long workOrderId);
    }

    public interface ClientReceiptRepository extends JpaRepository<ClientReceipt, Long> {
        List<ClientReceipt> findAllByOrderByReceiptDateDescIdDesc();

        List<ClientReceipt> findByWorkOrderIdOrderByReceiptDateAscIdAsc(Long workOrderId);

        List<ClientReceipt> findByBillIdOrderByReceiptDateAscIdAsc(Long billId);

        boolean existsByWorkOrderId(Long workOrderId);

        boolean existsByBillId(Long billId);

        @Query("select r.billId, sum(r.amount) from ClientReceipt r where r.billId in :billIds group by r.billId")
        List<Object[]> receivedByBill(Collection<Long> billIds);

        @Query("select r.workOrderId, sum(r.amount) from ClientReceipt r group by r.workOrderId")
        List<Object[]> receivedByWorkOrder();
    }
}
