package com.ccms.reporting;

import com.ccms.common.TenantGuard;
import com.ccms.labour.Labour;
import com.ccms.labour.LabourRepositories.LabourRepository;
import com.ccms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only aggregates over the cost ledger. Native SQL is NOT tenant-filtered by
 * Hibernate, so every query here binds :cid explicitly.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final LabourRepository labours;

    public record Filter(LocalDate from, LocalDate to, Long clientId, Long siteId, Long buildingId, Long floorId) {
    }

    public record ItemCostRow(Long workItemId, String code, String nameBn, String nameEn, String uom,
                              BigDecimal days, BigDecimal otHours, BigDecimal amount, long workers) {
    }

    public record SiteItemRow(Long siteId, String siteName, Long workItemId, BigDecimal days, BigDecimal amount) {
    }

    public record ItemCostReport(Filter filter, List<ItemCostRow> items, List<SiteItemRow> bySite,
                                 BigDecimal totalDays, BigDecimal totalAmount) {
    }

    public record LabourLine(LocalDate date, Long siteId, String siteName, String buildingName, String floorName,
                             String unitName, Long workItemId, String itemBn, String itemEn,
                             BigDecimal days, BigDecimal otHours, BigDecimal amount) {
    }

    public record LabourItemSummary(Long workItemId, String itemBn, String itemEn, BigDecimal days, BigDecimal amount) {
    }

    public record LabourSiteSummary(Long siteId, String siteName, BigDecimal days, BigDecimal amount) {
    }

    public record LabourHistory(Labour labour, LocalDate from, LocalDate to, List<LabourLine> lines,
                                List<LabourItemSummary> byItem, List<LabourSiteSummary> bySite,
                                BigDecimal periodDays, BigDecimal periodAmount,
                                BigDecimal totalEarned, BigDecimal totalPaid, BigDecimal due) {
    }

    public record LabourBalance(Long labourId, String name, String skillTier, boolean active,
                                BigDecimal earned, BigDecimal paid, BigDecimal due) {
    }

    public record NamedAmount(Long id, String name, String nameEn, BigDecimal amount) {
    }

    public record DayAmount(LocalDate date, BigDecimal amount) {
    }

    public record Dashboard(LocalDate from, LocalDate to, BigDecimal periodCost, BigDecimal todayCost,
                            long workersToday, long activeSites, long activeLabours, BigDecimal totalDue,
                            List<NamedAmount> byItem, List<NamedAmount> bySite, List<DayAmount> trend) {
    }

    // ------------------------------------------------------------------ FR-3.1 item-wise cost

    public ItemCostReport itemCost(Filter f) {
        MapSqlParameterSource p = params(f);
        String where = where(f);

        List<ItemCostRow> items = jdbc.query("""
                SELECT w.id, w.code, w.name_bn, w.name_en, w.uom,
                       SUM(e.days) days, SUM(e.ot_hours) ot, SUM(e.amount) amount,
                       COUNT(DISTINCT e.labour_id) workers
                FROM cost_entry e
                JOIN work_item w ON w.id = e.work_item_id
                JOIN site s ON s.id = e.site_id
                """ + where + """
                GROUP BY w.id, w.code, w.name_bn, w.name_en, w.uom, w.sort_order
                HAVING SUM(e.amount) <> 0 OR SUM(e.days) <> 0
                ORDER BY w.sort_order, w.name_en""", p,
                (rs, i) -> new ItemCostRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getLong(9)));

        List<SiteItemRow> bySite = jdbc.query("""
                SELECT s.id, s.name, e.work_item_id, SUM(e.days), SUM(e.amount)
                FROM cost_entry e
                JOIN site s ON s.id = e.site_id
                """ + where + """
                GROUP BY s.id, s.name, e.work_item_id
                HAVING SUM(e.amount) <> 0 OR SUM(e.days) <> 0
                ORDER BY s.name""", p,
                (rs, i) -> new SiteItemRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4), rs.getBigDecimal(5)));

        BigDecimal days = items.stream().map(ItemCostRow::days).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = items.stream().map(ItemCostRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ItemCostReport(f, items, bySite, days, amount);
    }

    // ------------------------------------------------------------------ FR-3.3 labour deployment history

    public LabourHistory labourHistory(Long labourId, LocalDate from, LocalDate to) {
        Labour labour = TenantGuard.own(labours.findById(labourId), "Labour", labourId);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("cid", TenantContext.require())
                .addValue("lid", labourId)
                .addValue("from", from)
                .addValue("to", to);

        List<LabourLine> lines = jdbc.query("""
                SELECT e.entry_date, s.id, s.name, b.name, f.name, u.name, w.id, w.name_bn, w.name_en,
                       SUM(e.days), SUM(e.ot_hours), SUM(e.amount)
                FROM cost_entry e
                JOIN site s ON s.id = e.site_id
                LEFT JOIN building b ON b.id = e.building_id
                LEFT JOIN floor f ON f.id = e.floor_id
                LEFT JOIN unit u ON u.id = e.unit_id
                LEFT JOIN work_item w ON w.id = e.work_item_id
                WHERE e.contractor_id = :cid AND e.labour_id = :lid AND e.entry_date BETWEEN :from AND :to
                GROUP BY e.entry_date, s.id, s.name, b.id, b.name, f.id, f.name, u.id, u.name, w.id, w.name_bn, w.name_en
                HAVING SUM(e.amount) <> 0 OR SUM(e.days) <> 0
                ORDER BY e.entry_date DESC, s.name""", p,
                (rs, i) -> new LabourLine(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getLong(7), rs.getString(8), rs.getString(9),
                        rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getBigDecimal(12)));

        List<LabourItemSummary> byItem = lines.stream()
                .collect(java.util.stream.Collectors.groupingBy(LabourLine::workItemId, java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .values().stream()
                .map(ls -> new LabourItemSummary(ls.get(0).workItemId(), ls.get(0).itemBn(), ls.get(0).itemEn(),
                        sum(ls.stream().map(LabourLine::days)), sum(ls.stream().map(LabourLine::amount))))
                .toList();
        List<LabourSiteSummary> bySite = lines.stream()
                .collect(java.util.stream.Collectors.groupingBy(LabourLine::siteId, java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .values().stream()
                .map(ls -> new LabourSiteSummary(ls.get(0).siteId(), ls.get(0).siteName(),
                        sum(ls.stream().map(LabourLine::days)), sum(ls.stream().map(LabourLine::amount))))
                .toList();

        LabourBalance bal = balances(labourId).stream().findFirst()
                .orElse(new LabourBalance(labourId, labour.getName(), labour.getSkillTier().name(), labour.isActive(),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        return new LabourHistory(labour, from, to, lines, byItem, bySite,
                sum(lines.stream().map(LabourLine::days)), sum(lines.stream().map(LabourLine::amount)),
                bal.earned(), bal.paid(), bal.due());
    }

    // ------------------------------------------------------------------ wages due

    /** All-time earned (ledger) vs paid (wages + advances) per worker. */
    public List<LabourBalance> balances(Long onlyLabourId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("cid", TenantContext.require())
                .addValue("lid", onlyLabourId);
        return jdbc.query("""
                SELECT l.id, l.name, l.skill_tier, l.active,
                       COALESCE(earned.amt, 0), COALESCE(paid.amt, 0)
                FROM labour l
                LEFT JOIN (SELECT labour_id, SUM(amount) amt FROM cost_entry
                           WHERE contractor_id = :cid AND source_type = 'LABOUR' GROUP BY labour_id) earned
                       ON earned.labour_id = l.id
                LEFT JOIN (SELECT labour_id, SUM(amount) amt FROM labour_payment
                           WHERE contractor_id = :cid GROUP BY labour_id) paid
                       ON paid.labour_id = l.id
                WHERE l.contractor_id = :cid AND (:lid IS NULL OR l.id = :lid)
                ORDER BY l.name""", p,
                (rs, i) -> {
                    BigDecimal earned = rs.getBigDecimal(5);
                    BigDecimal paid = rs.getBigDecimal(6);
                    return new LabourBalance(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                            earned, paid, earned.subtract(paid));
                });
    }

    // ------------------------------------------------------------------ FR-6.2 dashboard (labour part)

    public Dashboard dashboard(LocalDate from, LocalDate to) {
        Long cid = TenantContext.require();
        LocalDate today = LocalDate.now();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("cid", cid).addValue("from", from).addValue("to", to).addValue("today", today);

        BigDecimal periodCost = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM cost_entry
                WHERE contractor_id = :cid AND entry_date BETWEEN :from AND :to""", p, BigDecimal.class);
        BigDecimal todayCost = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM cost_entry
                WHERE contractor_id = :cid AND entry_date = :today""", p, BigDecimal.class);
        Long workersToday = jdbc.queryForObject("""
                SELECT COUNT(*) FROM attendance WHERE contractor_id = :cid AND work_date = :today""", p, Long.class);
        Long activeSites = jdbc.queryForObject("""
                SELECT COUNT(*) FROM site WHERE contractor_id = :cid AND status = 'ACTIVE'""", p, Long.class);
        Long activeLabours = jdbc.queryForObject("""
                SELECT COUNT(*) FROM labour WHERE contractor_id = :cid AND active = TRUE""", p, Long.class);
        BigDecimal totalDue = balances(null).stream().map(LabourBalance::due)
                .filter(d -> d.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<NamedAmount> byItem = jdbc.query("""
                SELECT w.id, w.name_bn, w.name_en, SUM(e.amount) amt
                FROM cost_entry e JOIN work_item w ON w.id = e.work_item_id
                WHERE e.contractor_id = :cid AND e.entry_date BETWEEN :from AND :to
                GROUP BY w.id, w.name_bn, w.name_en
                HAVING SUM(e.amount) <> 0
                ORDER BY amt DESC""", p,
                (rs, i) -> new NamedAmount(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)));
        List<NamedAmount> bySite = jdbc.query("""
                SELECT s.id, s.name, s.name, SUM(e.amount) amt
                FROM cost_entry e JOIN site s ON s.id = e.site_id
                WHERE e.contractor_id = :cid AND e.entry_date BETWEEN :from AND :to
                GROUP BY s.id, s.name
                HAVING SUM(e.amount) <> 0
                ORDER BY amt DESC""", p,
                (rs, i) -> new NamedAmount(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)));
        List<DayAmount> trend = jdbc.query("""
                SELECT entry_date, SUM(amount) FROM cost_entry
                WHERE contractor_id = :cid AND entry_date BETWEEN :from AND :to
                GROUP BY entry_date ORDER BY entry_date""", p,
                (rs, i) -> new DayAmount(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)));

        return new Dashboard(from, to, periodCost, todayCost, workersToday, activeSites, activeLabours, totalDue,
                byItem, bySite, trend);
    }

    // ------------------------------------------------------------------ helpers

    private static MapSqlParameterSource params(Filter f) {
        return new MapSqlParameterSource()
                .addValue("cid", TenantContext.require())
                .addValue("from", f.from())
                .addValue("to", f.to())
                .addValue("clientId", f.clientId())
                .addValue("siteId", f.siteId())
                .addValue("buildingId", f.buildingId())
                .addValue("floorId", f.floorId());
    }

    private static String where(Filter f) {
        StringBuilder sb = new StringBuilder("WHERE e.contractor_id = :cid AND e.entry_date BETWEEN :from AND :to\n");
        if (f.clientId() != null) sb.append(" AND s.client_id = :clientId\n");
        if (f.siteId() != null) sb.append(" AND e.site_id = :siteId\n");
        if (f.buildingId() != null) sb.append(" AND e.building_id = :buildingId\n");
        if (f.floorId() != null) sb.append(" AND e.floor_id = :floorId\n");
        return sb.toString();
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> s) {
        return s.reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
