package com.ccms.labour;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LabourRepositories {

    private LabourRepositories() {
    }

    public interface LabourRepository extends JpaRepository<Labour, Long> {
        List<Labour> findAllByOrderByNameAsc();
    }

    public interface RateCardRepository extends JpaRepository<RateCard, Long> {
        List<RateCard> findAllByOrderBySkillTierAscEffectiveFromDesc();

        @Query("""
                select r from RateCard r
                where r.skillTier = :tier and r.effectiveFrom <= :date
                  and (r.siteId = :siteId or r.siteId is null)
                order by case when r.siteId is null then 1 else 0 end, r.effectiveFrom desc, r.id desc""")
        List<RateCard> candidates(SkillTier tier, Long siteId, LocalDate date);
    }

    public interface LabourPaymentRepository extends JpaRepository<LabourPayment, Long> {
        List<LabourPayment> findByLabourIdOrderByPayDateDescIdDesc(Long labourId);

        @Query("select coalesce(sum(p.amount), 0) from LabourPayment p where p.labourId = :labourId")
        BigDecimal totalPaid(Long labourId);

        @Query("""
                select p from LabourPayment p
                where p.payDate between :from and :to
                order by p.payDate desc, p.id desc""")
        List<LabourPayment> between(LocalDate from, LocalDate to);
    }
}
