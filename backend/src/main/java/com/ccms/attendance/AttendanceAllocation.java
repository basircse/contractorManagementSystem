package com.ccms.attendance;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Part of a worker's day spent on one work item at one location, e.g. 0.5 day Brickwork. */
@Getter
@Setter
@Entity
@Table(name = "attendance_allocation")
public class AttendanceAllocation extends TenantEntity {

    @Column(nullable = false)
    private Long attendanceId;

    @Column(nullable = false)
    private Long siteId;
    private Long buildingId;
    private Long floorId;
    private Long unitId;

    @Column(nullable = false)
    private Long workItemId;

    @Column(nullable = false)
    private BigDecimal dayFraction;
    @Column(nullable = false)
    private BigDecimal otHours = BigDecimal.ZERO;
}
