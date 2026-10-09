package com.ccms.expense;

import com.ccms.audit.AuditService;
import com.ccms.common.Money;
import com.ccms.common.PayMethod;
import com.ccms.expense.ExpenseRepositories.PartyPaymentRepository;
import com.ccms.expense.ExpenseRepositories.PartyRepository;
import com.ccms.common.TenantGuard;
import com.ccms.tenant.TenantContext;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Third parties and what the contractor owes them. Charges come from the cost ledger
 * (purchases, rent, sub-contract bills, credit expenses); payments from party_payment.
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class PartyController {

    private final PartyRepository parties;
    private final PartyPaymentRepository payments;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public record PartyRequest(@NotBlank @Size(max = 150) String name, @NotNull Party.Type type,
                               @Size(max = 150) String trade, @Size(max = 30) String phone,
                               @Size(max = 500) String address, @Size(max = 500) String note, Boolean active) {
    }

    public record PaymentRequest(@NotNull Long partyId, @NotNull LocalDate payDate,
                                 @NotNull @Positive @Digits(integer = 14, fraction = 2) BigDecimal amount,
                                 @NotNull PayMethod method, @Size(max = 100) String reference,
                                 @Size(max = 500) String note) {
    }

    public record PartyBalance(@JsonUnwrapped Party party, BigDecimal charged, BigDecimal paid, BigDecimal due) {
    }

    /** One statement row: a charge (cost document) or a payment. */
    public record StatementLine(LocalDate date, String kind, Long sourceId, String siteName,
                                BigDecimal charge, BigDecimal payment, BigDecimal balance, String note) {
    }

    public record Statement(PartyBalance party, List<StatementLine> lines) {
    }

    public record PaymentView(@JsonUnwrapped PartyPayment payment, String partyName) {
    }

    // ------------------------------------------------------------------ parties

    @GetMapping("/parties")
    @Transactional(readOnly = true)
    public List<PartyBalance> list(@RequestParam(required = false) Party.Type type,
                                   @RequestParam(defaultValue = "false") boolean includeInactive) {
        Map<Long, BigDecimal> charged = chargedByParty();
        Map<Long, BigDecimal> paid = new HashMap<>();
        for (Object[] r : payments.paidByParty()) paid.put((Long) r[0], (BigDecimal) r[1]);
        return parties.findAllByOrderByNameAsc().stream()
                .filter(p -> type == null || type == p.getType())
                .filter(p -> includeInactive || p.isActive())
                .map(p -> balance(p, charged.get(p.getId()), paid.get(p.getId())))
                .toList();
    }

    @GetMapping("/parties/{id}/statement")
    @Transactional(readOnly = true)
    public Statement statement(@PathVariable Long id) {
        Party p = find(id);
        MapSqlParameterSource args = new MapSqlParameterSource().addValue("cid", TenantContext.require()).addValue("pid", id);
        List<StatementLine> rows = new ArrayList<>(jdbc.query("""
                SELECT MIN(e.entry_date) d, e.source_type, e.source_id, MAX(s.name), SUM(e.amount),
                       MAX(CASE e.source_type
                               WHEN 'MATERIAL' THEN p.invoice_no
                               WHEN 'SUBCONTRACT' THEN sc.title
                               WHEN 'EXPENSE' THEN x.description
                               WHEN 'RENTAL' THEN COALESCE(r.description, m.name_bn) END)
                FROM cost_entry e
                JOIN site s ON s.id = e.site_id
                LEFT JOIN purchase p ON e.source_type = 'MATERIAL' AND p.id = e.source_id
                LEFT JOIN subcontract_bill sb ON e.source_type = 'SUBCONTRACT' AND sb.id = e.source_id
                LEFT JOIN subcontract sc ON sc.id = sb.subcontract_id
                LEFT JOIN site_expense x ON e.source_type = 'EXPENSE' AND x.id = e.source_id
                LEFT JOIN rental_event re ON e.source_type = 'RENTAL' AND re.id = e.source_id
                LEFT JOIN rental r ON r.id = re.rental_id
                LEFT JOIN material m ON m.id = r.material_id
                WHERE e.contractor_id = :cid AND e.party_id = :pid
                GROUP BY e.source_type, e.source_id
                HAVING SUM(e.amount) <> 0""", args,
                (rs, i) -> new StatementLine(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getLong(3), rs.getString(4),
                        rs.getBigDecimal(5), BigDecimal.ZERO, BigDecimal.ZERO, rs.getString(6))));
        for (PartyPayment pay : payments.findByPartyIdOrderByPayDateAscIdAsc(id)) {
            rows.add(new StatementLine(pay.getPayDate(), "PAYMENT", pay.getId(), null, BigDecimal.ZERO, pay.getAmount(),
                    BigDecimal.ZERO, joinNote(pay.getMethod().name(), pay.getReference(), pay.getNote())));
        }
        rows.sort(Comparator.comparing(StatementLine::date).thenComparing(l -> "PAYMENT".equals(l.kind()) ? 1 : 0));
        List<StatementLine> withBalance = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (StatementLine l : rows) {
            running = running.add(l.charge()).subtract(l.payment());
            withBalance.add(new StatementLine(l.date(), l.kind(), l.sourceId(), l.siteName(), Money.of(l.charge()),
                    Money.of(l.payment()), Money.of(running), l.note()));
        }
        BigDecimal charged = Money.sum(withBalance, StatementLine::charge);
        BigDecimal paid = Money.sum(withBalance, StatementLine::payment);
        return new Statement(balance(p, charged, paid), withBalance);
    }

    @PostMapping("/parties")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Party create(@Valid @RequestBody PartyRequest req) {
        return apply(new Party(), req);
    }

    @PutMapping("/parties/{id}")
    @Transactional
    public Party update(@PathVariable Long id, @Valid @RequestBody PartyRequest req) {
        return apply(find(id), req);
    }

    // ------------------------------------------------------------------ payments

    @GetMapping("/party-payments")
    @Transactional(readOnly = true)
    public List<PaymentView> listPayments(@RequestParam(required = false) Long partyId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Map<Long, String> names = new HashMap<>();
        parties.findAll().forEach(p -> names.put(p.getId(), p.getName()));
        List<PartyPayment> rows;
        if (partyId != null) {
            rows = new ArrayList<>(payments.findByPartyIdOrderByPayDateAscIdAsc(partyId));
            Collections.reverse(rows);
        } else {
            LocalDate end = to != null ? to : LocalDate.now();
            rows = payments.between(from != null ? from : end.minusDays(30), end);
        }
        return rows.stream().map(p -> new PaymentView(p, names.get(p.getPartyId()))).toList();
    }

    @PostMapping("/party-payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PartyPayment pay(@Valid @RequestBody PaymentRequest req) {
        find(req.partyId());
        PartyPayment p = new PartyPayment();
        p.setPartyId(req.partyId());
        p.setPayDate(req.payDate());
        p.setAmount(Money.of(req.amount()));
        p.setMethod(req.method());
        p.setReference(req.reference());
        p.setNote(req.note());
        payments.save(p);
        audit.log("PARTY_PAYMENT", "PARTY", req.partyId(), p.getAmount().toString());
        return p;
    }

    @DeleteMapping("/party-payments/{id}")
    @Transactional
    public void deletePayment(@PathVariable Long id) {
        PartyPayment p = TenantGuard.own(payments.findById(id), "PartyPayment", id);
        if (p.getPurchaseId() != null) {
            throw com.ccms.income.IncomeSupport.invalidState("PAID_WITH_PURCHASE", "Edit the purchase to change this payment");
        }
        payments.delete(p);
        audit.log("PARTY_PAYMENT_DELETED", "PARTY", p.getPartyId(), p.getAmount().toString());
    }

    // ------------------------------------------------------------------ helpers

    private Map<Long, BigDecimal> chargedByParty() {
        Map<Long, BigDecimal> map = new HashMap<>();
        jdbc.query("""
                SELECT party_id, SUM(amount) FROM cost_entry
                WHERE contractor_id = :cid AND party_id IS NOT NULL GROUP BY party_id""",
                new MapSqlParameterSource("cid", TenantContext.require()),
                rs -> {
                    map.put(rs.getLong(1), rs.getBigDecimal(2));
                });
        return map;
    }

    private static PartyBalance balance(Party p, BigDecimal charged, BigDecimal paid) {
        BigDecimal c = Money.of(charged);
        BigDecimal d = Money.of(paid);
        return new PartyBalance(p, c, d, Money.of(c.subtract(d)));
    }

    private static String joinNote(String... parts) {
        return String.join(" · ", Arrays.stream(parts).filter(s -> s != null && !s.isBlank()).toList());
    }

    private Party apply(Party p, PartyRequest req) {
        p.setName(req.name().trim());
        p.setType(req.type());
        p.setTrade(req.trade());
        p.setPhone(req.phone());
        p.setAddress(req.address());
        p.setNote(req.note());
        if (req.active() != null) {
            p.setActive(req.active());
        }
        return parties.save(p);
    }

    private Party find(Long id) {
        return TenantGuard.own(parties.findById(id), "Party", id);
    }
}
