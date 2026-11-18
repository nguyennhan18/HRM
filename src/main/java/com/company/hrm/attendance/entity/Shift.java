package com.company.hrm.attendance.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.Getter;

@Entity
@Table(name = "shifts")
@Getter
public class Shift extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_start")
    private LocalTime breakStart;

    @Column(name = "break_end")
    private LocalTime breakEnd;

    @Column(nullable = false)
    private boolean overnight;

    @Column(name = "is_default", nullable = false)
    private boolean defaultShift;

    protected Shift() {
    }

    public Shift(String code, String name, LocalTime startTime, LocalTime endTime,
            LocalTime breakStart, LocalTime breakEnd, boolean overnight, boolean defaultShift) {
        this.code = code;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
        this.overnight = overnight;
        this.defaultShift = defaultShift;
    }

    public void update(String name, LocalTime startTime, LocalTime endTime,
            LocalTime breakStart, LocalTime breakEnd, boolean overnight, boolean defaultShift) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
        this.overnight = overnight;
        this.defaultShift = defaultShift;
    }

    public void setDefaultShift(boolean defaultShift) {
        this.defaultShift = defaultShift;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Shift s && getId() != null && getId().equals(s.getId()));
    }

    @Override
    public int hashCode() {
        return Shift.class.hashCode();
    }

    @Override
    public String toString() {
        return "Shift[id=" + getId() + ", code=" + code + "]";
    }
}
