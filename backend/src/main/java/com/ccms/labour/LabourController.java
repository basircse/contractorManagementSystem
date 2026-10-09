package com.ccms.labour;

import com.ccms.audit.AuditService;
import com.ccms.common.TenantGuard;
import com.ccms.hierarchy.HierarchyService;
import com.ccms.labour.LabourRepositories.*;
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

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class LabourController {

    private final LabourRepository labours;
    private final RateCardRepository rateCards;
    private final LabourPaymentRepository payments;
    private final HierarchyService hierarchy;
    private final AuditService audit;

    public record LabourRequest(@NotBlank @Size(max = 150) String name, @Size(max = 30) String phone,
                                @Size(max = 30) String nid, @Size(max = 500) String address,
                                @NotNull SkillTier skillTier, LocalDate joinedOn, Boolean active) {
    }

    public record RateCardRequest(@NotNull SkillTier skillTier, Long siteId,
                                  @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal dailyRate,
                                  @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal otHourlyRate,
                                  @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal skillAllowance,
                                  @NotNull LocalDate effectiveFrom) {
    }

    public record PaymentRequest(@NotNull Long labourId, @NotNull LocalDate payDate,
                                 @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
                                 @NotNull LabourPayment.Type type, @Size(max = 500) String note) {
    }

    // ------------------------------------------------------------------ labour

    @GetMapping("/labours")
    public List<Labour> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return labours.findAllByOrderByNameAsc().stream().filter(l -> includeInactive || l.isActive()).toList();
    }

    @GetMapping("/labours/{id}")
    public Labour get(@PathVariable Long id) {
        return find(id);
    }

    @PostMapping("/labours")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Labour create(@Valid @RequestBody LabourRequest req) {
        return apply(new Labour(), req);
    }

    @PutMapping("/labours/{id}")
    @Transactional
    public Labour update(@PathVariable Long id, @Valid @RequestBody LabourRequest req) {
        return apply(find(id), req);
    }

    private Labour apply(Labour l, LabourRequest req) {
        l.setName(req.name().trim());
        l.setPhone(req.phone());
        l.setNid(req.nid());
        l.setAddress(req.address());
        l.setSkillTier(req.skillTier());
        l.setJoinedOn(req.joinedOn());
        if (req.active() != null) {
            l.setActive(req.active());
        }
        return labours.save(l);
    }

    public Labour find(Long id) {
        return TenantGuard.own(labours.findById(id), "Labour", id);
    }

    // ------------------------------------------------------------------ rate cards

    @GetMapping("/rate-cards")
    public List<RateCard> listRates() {
        return rateCards.findAllByOrderBySkillTierAscEffectiveFromDesc();
    }

    @PostMapping("/rate-cards")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RateCard createRate(@Valid @RequestBody RateCardRequest req) {
        RateCard r = applyRate(new RateCard(), req);
        audit.log("RATE_CARD_CREATED", "RATE_CARD", r.getId(), r.getSkillTier() + " " + r.getDailyRate());
        return r;
    }

    /** Editing a rate card only affects attendance saved afterwards; posted costs keep their snapshot. */
    @PutMapping("/rate-cards/{id}")
    @Transactional
    public RateCard updateRate(@PathVariable Long id, @Valid @RequestBody RateCardRequest req) {
        RateCard r = applyRate(TenantGuard.own(rateCards.findById(id), "RateCard", id), req);
        audit.log("RATE_CARD_UPDATED", "RATE_CARD", id, r.getSkillTier() + " " + r.getDailyRate());
        return r;
    }

    @DeleteMapping("/rate-cards/{id}")
    @Transactional
    public void deleteRate(@PathVariable Long id) {
        RateCard r = TenantGuard.own(rateCards.findById(id), "RateCard", id);
        rateCards.delete(r);
        audit.log("RATE_CARD_DELETED", "RATE_CARD", id, r.getSkillTier() + " " + r.getDailyRate());
    }

    private RateCard applyRate(RateCard r, RateCardRequest req) {
        if (req.siteId() != null) {
            hierarchy.site(req.siteId());
        }
        r.setSkillTier(req.skillTier());
        r.setSiteId(req.siteId());
        r.setDailyRate(req.dailyRate());
        r.setOtHourlyRate(req.otHourlyRate() != null ? req.otHourlyRate() : BigDecimal.ZERO);
        r.setSkillAllowance(req.skillAllowance() != null ? req.skillAllowance() : BigDecimal.ZERO);
        r.setEffectiveFrom(req.effectiveFrom());
        return rateCards.save(r);
    }

    // ------------------------------------------------------------------ payments

    @GetMapping("/labour-payments")
    public List<LabourPayment> listPayments(
            @RequestParam(required = false) Long labourId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (labourId != null) {
            return payments.findByLabourIdOrderByPayDateDescIdDesc(labourId);
        }
        LocalDate end = to != null ? to : LocalDate.now();
        return payments.between(from != null ? from : end.minusDays(30), end);
    }

    @PostMapping("/labour-payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LabourPayment createPayment(@Valid @RequestBody PaymentRequest req) {
        find(req.labourId());
        LabourPayment p = new LabourPayment();
        p.setLabourId(req.labourId());
        p.setPayDate(req.payDate());
        p.setAmount(req.amount());
        p.setType(req.type());
        p.setNote(req.note());
        payments.save(p);
        audit.log("LABOUR_PAYMENT", "LABOUR", req.labourId(), req.type() + " " + req.amount());
        return p;
    }

    @DeleteMapping("/labour-payments/{id}")
    @Transactional
    public void deletePayment(@PathVariable Long id) {
        LabourPayment p = TenantGuard.own(payments.findById(id), "LabourPayment", id);
        payments.delete(p);
        audit.log("LABOUR_PAYMENT_DELETED", "LABOUR", p.getLabourId(), p.getType() + " " + p.getAmount());
    }
}
