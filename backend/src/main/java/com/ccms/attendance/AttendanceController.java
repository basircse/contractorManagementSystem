package com.ccms.attendance;

import com.ccms.attendance.AttendanceService.AttendanceDto;
import com.ccms.attendance.AttendanceService.Slice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/app/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService service;

    public record AssignRequest(@NotNull LocalDate workDate, @NotEmpty List<Long> labourIds, @NotNull @Valid Slice slice) {
    }

    public record DayRequest(@NotNull Long labourId, @NotNull LocalDate workDate, @Size(max = 500) String note,
                             @NotNull List<Slice> allocations) {
    }

    @GetMapping
    public List<AttendanceDto> day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(required = false) Long siteId) {
        return service.day(date, siteId);
    }

    /** Quick tagging: N workers x one work item at one location. */
    @PostMapping("/assign")
    public List<AttendanceDto> assign(@Valid @RequestBody AssignRequest req) {
        return service.assign(req.workDate(), req.labourIds(), req.slice());
    }

    /** Replace a worker's whole day. */
    @PutMapping("/day")
    public AttendanceDto replaceDay(@Valid @RequestBody DayRequest req) {
        return service.replaceDay(req.labourId(), req.workDate(), req.note(), req.allocations());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
