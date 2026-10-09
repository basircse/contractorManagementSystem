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
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only aggregates over the cost ledger and the income documents. Native SQL is NOT
 * tenant-filtered by Hibernate, so every query here binds :cid explicitly.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final LabourRepository labours;

    public record Filter(LocalDate from, LocalDate to, Long clientId, Long siteId, Long buildingId, Long floorId) {
    }

    public record CostSplit(BigDecimal labour, BigDecimal material, BigDecimal rental, BigDecimal subcontract,
                            BigDecimal expense, BigDecimal total) {
    }

    public record ItemCostRow(Long workItemId, String code, String nameBn, String nameEn, String uom,
                              BigDecimal days, BigDecimal otHours, BigDecimal amount, long workers,
                              BigDecimal labour, BigDecimal material, BigDecimal rental, BigDecimal subcontract,
                              BigDecimal expense) {
    }

    public record SiteItemRow(Long siteId, String siteName, Long workItemId, BigDecimal days, BigDecimal amount) {
    }

    /** generalAmount = costs not tagged to any work item (site overheads). */
    public record ItemCostReport(Filter filter, List<ItemCostRow> items, List<SiteItemRow> bySite,
                                 BigDecimal totalDays, BigDecimal totalAmount, BigDecimal generalAmount,
                                 CostSplit split) {
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
                            List<NamedAmount> byItem, List<NamedAmount> bySite, List<DayAmount> trend,
                            BigDecimal periodBilled, BigDecimal periodReceived, BigDecimal receivable,
                            BigDecimal overdueAmount, long overdueBills, long milestonesDue,
                            BigDecimal partyDue, CostSplit periodSplit) {
    }

    public record SiteProfit(Long siteId, String siteName, String clientName, BigDecimal contractValue,
                             BigDecimal billed, BigDecimal received, CostSplit cost, BigDecimal profit,
                             BigDecimal marginPercent) {
    }

    /** workItemId null = billed lines / costs not tagged to a work item. */
    public record ItemProfit(Long workItemId, String nameBn, String nameEn, BigDecimal contractValue,
                             BigDecimal billed, CostSplit cost, BigDecimal profit) {
    }

    /** Income = submitted bills (gross amount); cost = ledger. */
    public record ProfitReport(LocalDate from, LocalDate to, Long siteId, List<SiteProfit> sites,
                               List<ItemProfit> items, SiteProfit total) {
    }

    /** Cost split by source; expects the ledger aliased as {@code e}. */
    private static final String SPLIT_COLUMNS = """
            COALESCE(SUM(CASE WHEN e.source_type = 'LABOUR' THEN e.amount END), 0) labour,
            COALESCE(SUM(CASE WHEN e.source_type = 'MATERIAL' THEN e.amount END), 0) material,
            COALESCE(SUM(CASE WHEN e.source_type = 'RENTAL' THEN e.amount END), 0) rental,
            COALESCE(SUM(CASE WHEN e.source_type = 'SUBCONTRACT' THEN e.amount END), 0) subcontract,
            COALESCE(SUM(CASE WHEN e.source_type NOT IN ('LABOUR', 'MATERIAL', 'RENTAL', 'SUBCONTRACT') THEN e.amount END), 0) expense,
            COALESCE(SUM(e.amount), 0) total
            """;

    // ------------------------------------------------------------------ FR-3.1 item-wise cost

    public ItemCostReport itemCost(Filter f) {
        MapSqlParameterSource p = params(f);
        String where = where(f);

        List<ItemCostRow> items = jdbc.query("""
                SELECT w.id, w.code, w.name_bn, w.name_en, w.uom,
                       SUM(e.days) days, SUM(e.ot_hours) ot, SUM(e.amount) amount,
                       COUNT(DISTINCT e.labour_id) workers,
                """ + SPLIT_COLUMNS + """
                FROM cost_entry e
                JOIN work_item w ON w.id = e.work_item_id
                JOIN site s ON s.id = e.site_id
                """ + where + """
                GROUP BY w.id, w.code, w.name_bn, w.name_en, w.uom, w.sort_order
                HAVING SUM(e.amount) <> 0 OR SUM(e.days) <> 0
                ORDER BY w.sort_order, w.name_en""", p,
                (rs, i) -> new ItemCostRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getLong(9),
                        rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getBigDecimal(12), rs.getBigDecimal(13),
                        rs.getBigDecimal(14)));

        List<SiteItemRow> bySite = jdbc.query("""
                SELECT s.id, s.name, e.work_item_id, SUM(e.days), SUM(e.amount)
                FROM cost_entry e
                JOIN site s ON s.id = e.site_id
                """ + where + """
                  AND e.work_item_id IS NOT NULL
                GROUP BY s.id, s.name, e.work_item_id
                HAVING SUM(e.amount) <> 0 OR SUM(e.days) <> 0
                ORDER BY s.name""", p,
                (rs, i) -> new SiteItemRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4), rs.getBigDecimal(5)));

        CostSplit split = jdbc.queryForObject("SELECT " + SPLIT_COLUMNS + """
                FROM cost_entry e
                JOIN site s ON s.id = e.site_id
                """ + where, p, (rs, i) -> split(rs, 1));

        BigDecimal days = sum(items.stream().map(ItemCostRow::days));
        BigDecimal amount = sum(items.stream().map(ItemCostRow::amount));
        return new ItemCostReport(f, items, bySite, days, amount, split.total().subtract(amount), split);
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

    // ------------------------------------------------------------------ FR-6.2 dashboard

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

        BigDecimal periodBilled = jdbc.queryForObject("""
                SELECT COALESCE(SUM(gross_amount), 0) FROM client_bill
                WHERE contractor_id = :cid AND status = 'SUBMITTED' AND bill_date BETWEEN :from AND :to""", p, BigDecimal.class);
        BigDecimal periodReceived = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM client_receipt
                WHERE contractor_id = :cid AND receipt_date BETWEEN :from AND :to""", p, BigDecimal.class);
        // Outstanding per work order (net billed - received); advances do not offset other work orders.
        BigDecimal receivable = jdbc.queryForObject("""
                SELECT COALESCE(SUM(GREATEST(COALESCE(b.net, 0) - COALESCE(r.got, 0), 0)), 0)
                FROM work_order w
                LEFT JOIN (SELECT work_order_id, SUM(net_amount) net FROM client_bill
                           WHERE contractor_id = :cid AND status = 'SUBMITTED' GROUP BY work_order_id) b ON b.work_order_id = w.id
                LEFT JOIN (SELECT work_order_id, SUM(amount) got FROM client_receipt
                           WHERE contractor_id = :cid GROUP BY work_order_id) r ON r.work_order_id = w.id
                WHERE w.contractor_id = :cid""", p, BigDecimal.class);
        // Overdue bill balances per work order, capped at what the work order still owes:
        // an advance received without a bill already covers part of the overdue amount.
        Object[] overdue = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN y.capped > 0 THEN y.n ELSE 0 END), 0), COALESCE(SUM(y.capped), 0) FROM (
                    SELECT o.n, LEAST(o.bal, GREATEST(COALESCE(nb.net, 0) - COALESCE(r.got, 0), 0)) capped
                    FROM (SELECT x.work_order_id, COUNT(*) n, SUM(x.bal) bal FROM (
                              SELECT b.work_order_id, b.net_amount - COALESCE(SUM(r.amount), 0) bal
                              FROM client_bill b LEFT JOIN client_receipt r ON r.bill_id = b.id
                              WHERE b.contractor_id = :cid AND b.status = 'SUBMITTED' AND b.due_date < :today
                              GROUP BY b.id, b.work_order_id, b.net_amount
                              HAVING bal > 0) x
                          GROUP BY x.work_order_id) o
                    LEFT JOIN (SELECT work_order_id, SUM(net_amount) net FROM client_bill
                               WHERE contractor_id = :cid AND status = 'SUBMITTED' GROUP BY work_order_id) nb
                           ON nb.work_order_id = o.work_order_id
                    LEFT JOIN (SELECT work_order_id, SUM(amount) got FROM client_receipt
                               WHERE contractor_id = :cid GROUP BY work_order_id) r
                           ON r.work_order_id = o.work_order_id) y""", p,
                (rs, i) -> new Object[]{rs.getLong(1), rs.getBigDecimal(2)});
        Long milestonesDue = jdbc.queryForObject("""
                SELECT COUNT(*) FROM billing_milestone m JOIN work_order w ON w.id = m.work_order_id
                WHERE m.contractor_id = :cid AND m.bill_id IS NULL AND m.due_date <= :today
                  AND w.status = 'ACTIVE'""", p, Long.class);
        BigDecimal partyDue = jdbc.queryForObject("""
                SELECT COALESCE(SUM(GREATEST(COALESCE(c.amt, 0) - COALESCE(pp.amt, 0), 0)), 0)
                FROM party pa
                LEFT JOIN (SELECT party_id, SUM(amount) amt FROM cost_entry
                           WHERE contractor_id = :cid AND party_id IS NOT NULL GROUP BY party_id) c ON c.party_id = pa.id
                LEFT JOIN (SELECT party_id, SUM(amount) amt FROM party_payment
                           WHERE contractor_id = :cid GROUP BY party_id) pp ON pp.party_id = pa.id
                WHERE pa.contractor_id = :cid""", p, BigDecimal.class);
        CostSplit periodSplit = jdbc.queryForObject("SELECT " + SPLIT_COLUMNS + """
                FROM cost_entry e
                WHERE e.contractor_id = :cid AND e.entry_date BETWEEN :from AND :to""", p, (rs, i) -> split(rs, 1));

        return new Dashboard(from, to, periodCost, todayCost, workersToday, activeSites, activeLabours, totalDue,
                byItem, bySite, trend, periodBilled, periodReceived, receivable, (BigDecimal) overdue[1],
                (Long) overdue[0], milestonesDue, partyDue, periodSplit);
    }

    // ------------------------------------------------------------------ FR-6 profitability

    public ProfitReport profit(LocalDate from, LocalDate to, Long siteId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("cid", TenantContext.require()).addValue("from", from).addValue("to", to)
                .addValue("siteId", siteId);
        String siteFilter = siteId != null ? " AND s.id = :siteId" : "";

        List<SiteProfit> sites = jdbc.query("""
                SELECT s.id, s.name, c.name,
                       COALESCE(wo.contract, 0), COALESCE(b.billed, 0), COALESCE(r.got, 0),
                       COALESCE(k.labour, 0), COALESCE(k.material, 0), COALESCE(k.rental, 0),
                       COALESCE(k.subcontract, 0), COALESCE(k.expense, 0), COALESCE(k.total, 0)
                FROM site s
                JOIN client c ON c.id = s.client_id
                LEFT JOIN (SELECT site_id, SUM(contract_value) contract FROM work_order
                           WHERE contractor_id = :cid AND status <> 'CANCELLED' GROUP BY site_id) wo ON wo.site_id = s.id
                LEFT JOIN (SELECT site_id, SUM(gross_amount) billed FROM client_bill
                           WHERE contractor_id = :cid AND status = 'SUBMITTED' AND bill_date BETWEEN :from AND :to
                           GROUP BY site_id) b ON b.site_id = s.id
                LEFT JOIN (SELECT w.site_id, SUM(r.amount) got FROM client_receipt r JOIN work_order w ON w.id = r.work_order_id
                           WHERE r.contractor_id = :cid AND r.receipt_date BETWEEN :from AND :to
                           GROUP BY w.site_id) r ON r.site_id = s.id
                LEFT JOIN (SELECT e.site_id,
                """ + SPLIT_COLUMNS + """
                           FROM cost_entry e WHERE e.contractor_id = :cid AND e.entry_date BETWEEN :from AND :to
                           GROUP BY e.site_id) k ON k.site_id = s.id
                WHERE s.contractor_id = :cid
                """ + siteFilter + """
                  AND (wo.contract IS NOT NULL OR b.billed IS NOT NULL OR r.got IS NOT NULL OR k.total IS NOT NULL)
                ORDER BY s.name""", p,
                (rs, i) -> siteProfit(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), split(rs, 7)));

        String woSite = siteId != null ? " AND w.site_id = :siteId" : "";
        String billSite = siteId != null ? " AND b.site_id = :siteId" : "";
        String costSite = siteId != null ? " AND e.site_id = :siteId" : "";
        List<ItemProfit> items = new ArrayList<>(jdbc.query("""
                SELECT wi.id, wi.name_bn, wi.name_en, COALESCE(ct.amt, 0), COALESCE(bl.amt, 0),
                       COALESCE(k.labour, 0), COALESCE(k.material, 0), COALESCE(k.rental, 0),
                       COALESCE(k.subcontract, 0), COALESCE(k.expense, 0), COALESCE(k.total, 0)
                FROM work_item wi
                LEFT JOIN (SELECT l.work_item_id, SUM(l.amount) amt FROM work_order_line l
                           JOIN work_order w ON w.id = l.work_order_id
                           WHERE l.contractor_id = :cid AND w.status <> 'CANCELLED'
                """ + woSite + """
                           GROUP BY l.work_item_id) ct ON ct.work_item_id = wi.id
                LEFT JOIN (SELECT l.work_item_id, SUM(l.amount) amt FROM client_bill_line l
                           JOIN client_bill b ON b.id = l.bill_id
                           WHERE l.contractor_id = :cid AND b.status = 'SUBMITTED' AND b.bill_date BETWEEN :from AND :to
                """ + billSite + """
                           GROUP BY l.work_item_id) bl ON bl.work_item_id = wi.id
                LEFT JOIN (SELECT e.work_item_id,
                """ + SPLIT_COLUMNS + """
                           FROM cost_entry e WHERE e.contractor_id = :cid AND e.entry_date BETWEEN :from AND :to
                """ + costSite + """
                           GROUP BY e.work_item_id) k ON k.work_item_id = wi.id
                WHERE wi.contractor_id = :cid
                  AND (ct.amt IS NOT NULL OR bl.amt IS NOT NULL OR k.total IS NOT NULL)
                ORDER BY wi.sort_order, wi.name_en""", p,
                (rs, i) -> {
                    CostSplit c = split(rs, 6);
                    return new ItemProfit(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                            rs.getBigDecimal(5), c, rs.getBigDecimal(5).subtract(c.total()));
                }));

        // Billed lines and costs without a work item (milestone bills, site overheads).
        BigDecimal unassignedBilled = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.amount), 0) FROM client_bill_line l JOIN client_bill b ON b.id = l.bill_id
                WHERE l.contractor_id = :cid AND b.status = 'SUBMITTED' AND l.work_item_id IS NULL
                  AND b.bill_date BETWEEN :from AND :to
                """ + billSite, p, BigDecimal.class);
        CostSplit unassignedCost = jdbc.queryForObject("SELECT " + SPLIT_COLUMNS + """
                FROM cost_entry e WHERE e.contractor_id = :cid AND e.work_item_id IS NULL
                  AND e.entry_date BETWEEN :from AND :to
                """ + costSite, p, (rs, i) -> split(rs, 1));
        if (unassignedBilled.signum() != 0 || unassignedCost.total().signum() != 0) {
            items.add(new ItemProfit(null, null, null, BigDecimal.ZERO, unassignedBilled, unassignedCost,
                    unassignedBilled.subtract(unassignedCost.total())));
        }

        CostSplit totalCost = new CostSplit(
                sum(sites.stream().map(x -> x.cost().labour())), sum(sites.stream().map(x -> x.cost().material())),
                sum(sites.stream().map(x -> x.cost().rental())), sum(sites.stream().map(x -> x.cost().subcontract())),
                sum(sites.stream().map(x -> x.cost().expense())), sum(sites.stream().map(x -> x.cost().total())));
        SiteProfit total = siteProfit(null, null, null, sum(sites.stream().map(SiteProfit::contractValue)),
                sum(sites.stream().map(SiteProfit::billed)), sum(sites.stream().map(SiteProfit::received)), totalCost);
        return new ProfitReport(from, to, siteId, sites, items, total);
    }

    // ------------------------------------------------------------------ helpers

    private static CostSplit split(ResultSet rs, int first) throws SQLException {
        return new CostSplit(rs.getBigDecimal(first), rs.getBigDecimal(first + 1), rs.getBigDecimal(first + 2),
                rs.getBigDecimal(first + 3), rs.getBigDecimal(first + 4), rs.getBigDecimal(first + 5));
    }

    private static SiteProfit siteProfit(Long id, String name, String client, BigDecimal contract, BigDecimal billed,
                                         BigDecimal received, CostSplit cost) {
        BigDecimal profit = billed.subtract(cost.total());
        BigDecimal margin = billed.signum() > 0
                ? profit.multiply(BigDecimal.valueOf(100)).divide(billed, 1, RoundingMode.HALF_UP) : null;
        return new SiteProfit(id, name, client, contract, billed, received, cost, profit, margin);
    }

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
