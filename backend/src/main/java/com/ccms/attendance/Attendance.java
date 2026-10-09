package com.ccms.attendance;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** One worker's day. How the day was spent lives in {@link AttendanceAllocation}. */
@Getter
@Setter
@Entity
@Table(name = "attendance")
public class Attendance extends TenantEntity {

    @Column(nullable = false)
    private Long labourId;
    @Column(nullable = false)
    private LocalDate workDate;
    private String note;
}
