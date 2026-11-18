package com.company.hrm.attendance.entity;

import com.company.hrm.common.entity.BaseEntity;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "attendance_records")
@Getter
public class AttendanceRecord extends BaseEntity {

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "check_in_at")
    private Instant checkInAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_source", length = 20)
    private AttendanceSource checkInSource;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_out_source", length = 20)
    private AttendanceSource checkOutSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttendanceStatus status;

    protected AttendanceRecord() {
    }

    private AttendanceRecord(
            UUID employeeId,
            UUID shiftId,
            LocalDate workDate,
            Instant checkInAt,
            AttendanceSource checkInSource,
            AttendanceStatus status) {
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.shiftId = Objects.requireNonNull(shiftId, "shiftId must not be null");
        this.workDate = Objects.requireNonNull(workDate, "workDate must not be null");
        this.checkInAt = Objects.requireNonNull(checkInAt, "checkInAt must not be null");
        this.checkInSource = Objects.requireNonNullElse(checkInSource, AttendanceSource.WEB);
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    /** Khởi tạo bản ghi ngày công khi check-in; đặt tạm MISSING_CHECKOUT cho đến khi check-out. */
    public static AttendanceRecord checkIn(
            UUID employeeId,
            UUID shiftId,
            LocalDate workDate,
            Instant checkInAt,
            AttendanceSource source) {
        return new AttendanceRecord(
                employeeId,
                shiftId,
                workDate,
                checkInAt,
                source,
                AttendanceStatus.MISSING_CHECKOUT);
    }

    /** Ghi nhận check-out cho phiên đang mở và cập nhật trạng thái tạm sang PRESENT. */
    public void recordCheckOut(Instant checkOutAt, AttendanceSource source) {
        if (this.checkInAt == null || this.checkOutAt != null) {
            throw new BusinessException(
                    ErrorCode.ATTENDANCE_CHECK_OUT_WITHOUT_CHECK_IN,
                    "Nhân viên chưa check-in hoặc đã check-out");
        }
        if (checkOutAt == null) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Thời điểm check-out không được để trống");
        }
        if (checkOutAt.isBefore(this.checkInAt)) {
            throw new BusinessException(
                    ErrorCode.ATTENDANCE_INVALID_ORDER,
                    "Thời điểm check-out không được trước thời điểm check-in");
        }
        this.checkOutAt = checkOutAt;
        this.checkOutSource = Objects.requireNonNullElse(source, AttendanceSource.WEB);
        this.status = AttendanceStatus.PRESENT;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof AttendanceRecord r && getId() != null && getId().equals(r.getId()));
    }

    @Override
    public int hashCode() {
        return AttendanceRecord.class.hashCode();
    }

    @Override
    public String toString() {
        return "AttendanceRecord[id=" + getId() + ", employeeId=" + employeeId + ", workDate=" + workDate
                + ", status=" + status + "]";
    }
}
