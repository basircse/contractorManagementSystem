package com.ccms.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class AttendanceRepositories {

    private AttendanceRepositories() {
    }

    public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
        Optional<Attendance> findByLabourIdAndWorkDate(Long labourId, LocalDate workDate);

        List<Attendance> findByWorkDate(LocalDate workDate);

        List<Attendance> findByLabourIdInAndWorkDate(Collection<Long> labourIds, LocalDate workDate);
    }

    public interface AllocationRepository extends JpaRepository<AttendanceAllocation, Long> {
        List<AttendanceAllocation> findByAttendanceIdIn(Collection<Long> attendanceIds);

        List<AttendanceAllocation> findByAttendanceId(Long attendanceId);

        @Modifying
        @Query("delete from AttendanceAllocation a where a.attendanceId = :attendanceId")
        void deleteByAttendanceId(Long attendanceId);
    }
}
