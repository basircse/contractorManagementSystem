package com.ccms.reporting;

import com.ccms.reporting.ReportService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** All report endpoints are GET, so the platform admin can view them read-only. */
@RestController
@RequestMapping("/api/app/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reports;

    @GetMapping("/item-cost")
    public ItemCostReport itemCost(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) Long clientId,
                                   @RequestParam(required = false) Long siteId,
                                   @RequestParam(required = false) Long buildingId,
                                   @RequestParam(required = false) Long floorId) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        return reports.itemCost(new Filter(start, end, clientId, siteId, buildingId, floorId));
    }

    @GetMapping("/labour/{labourId}")
    public LabourHistory labour(@PathVariable Long labourId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        return reports.labourHistory(labourId, start, end);
    }

    @GetMapping("/labour-balances")
    public List<LabourBalance> balances() {
        return reports.balances(null);
    }

    /** Profit by site and by work item. Default period: all time up to today. */
    @GetMapping("/profit")
    public ProfitReport profit(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) Long siteId) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : LocalDate.of(2000, 1, 1);
        return reports.profit(start, end, siteId);
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        return reports.dashboard(start, end);
    }
}
