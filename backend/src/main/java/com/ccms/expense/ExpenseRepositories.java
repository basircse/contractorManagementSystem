package com.ccms.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public final class ExpenseRepositories {

    private ExpenseRepositories() {
    }

    public interface PartyRepository extends JpaRepository<Party, Long> {
        List<Party> findAllByOrderByNameAsc();
    }

    public interface PartyPaymentRepository extends JpaRepository<PartyPayment, Long> {
        List<PartyPayment> findByPartyIdOrderByPayDateAscIdAsc(Long partyId);

        List<PartyPayment> findByPurchaseId(Long purchaseId);

        @Query("""
                select p from PartyPayment p where p.payDate between :from and :to
                order by p.payDate desc, p.id desc""")
        List<PartyPayment> between(LocalDate from, LocalDate to);

        @Query("select p.partyId, sum(p.amount) from PartyPayment p group by p.partyId")
        List<Object[]> paidByParty();
    }

    public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
        @Query("""
                select p from Purchase p where p.purchaseDate between :from and :to
                order by p.purchaseDate desc, p.id desc""")
        List<Purchase> between(LocalDate from, LocalDate to);

        boolean existsByPartyId(Long partyId);
    }

    public interface PurchaseLineRepository extends JpaRepository<PurchaseLine, Long> {
        List<PurchaseLine> findByPurchaseIdOrderByIdAsc(Long purchaseId);

        List<PurchaseLine> findByPurchaseIdIn(java.util.Collection<Long> purchaseIds);

        @Modifying
        @Query("delete from PurchaseLine l where l.purchaseId = :purchaseId")
        void deleteByPurchaseId(Long purchaseId);
    }

    public interface RentalRepository extends JpaRepository<Rental, Long> {
        List<Rental> findAllByOrderByStatusAscStartDateDescIdDesc();
    }

    public interface RentalEventRepository extends JpaRepository<RentalEvent, Long> {
        List<RentalEvent> findByRentalIdOrderByIdAsc(Long rentalId);

        List<RentalEvent> findByRentalIdIn(java.util.Collection<Long> rentalIds);
    }

    public interface SubcontractRepository extends JpaRepository<Subcontract, Long> {
        List<Subcontract> findAllByOrderByStatusAscIdDesc();
    }

    public interface SubcontractBillRepository extends JpaRepository<SubcontractBill, Long> {
        List<SubcontractBill> findBySubcontractIdOrderByBillDateAscIdAsc(Long subcontractId);

        boolean existsBySubcontractId(Long subcontractId);

        @Query("select b.subcontractId, sum(b.quantity), sum(b.amount) from SubcontractBill b group by b.subcontractId")
        List<Object[]> totalsBySubcontract();
    }

    public interface SiteExpenseRepository extends JpaRepository<SiteExpense, Long> {
        @Query("""
                select e from SiteExpense e where e.expenseDate between :from and :to
                order by e.expenseDate desc, e.id desc""")
        List<SiteExpense> between(LocalDate from, LocalDate to);
    }
}
