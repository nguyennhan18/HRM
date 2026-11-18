package com.company.hrm.attendance.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;

@Entity
@Table(name = "holidays")
@Getter
public class Holiday extends BaseEntity {

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal multiplier;

    protected Holiday() {
    }

    public Holiday(LocalDate holidayDate, String name, BigDecimal multiplier) {
        this.holidayDate = holidayDate;
        this.name = name;
        this.multiplier = multiplier;
    }

    public void update(LocalDate holidayDate, String name, BigDecimal multiplier) {
        this.holidayDate = holidayDate;
        this.name = name;
        this.multiplier = multiplier;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Holiday h && getId() != null && getId().equals(h.getId()));
    }

    @Override
    public int hashCode() {
        return Holiday.class.hashCode();
    }

    @Override
    public String toString() {
        return "Holiday[id=" + getId() + ", holidayDate=" + holidayDate + "]";
    }
}
