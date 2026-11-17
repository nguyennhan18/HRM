package com.company.hrm.testsupport;

import com.company.hrm.employee.dto.CreateEmployeeRequest;
import com.company.hrm.employee.entity.Gender;
import java.time.LocalDate;
import java.util.UUID;

/** Synthetic identifiers only; no real personal data. */
public class EmployeeFixture {
    private String name = "Nhân viên thử nghiệm";
    private String email;
    private String cccd;
    private String bank;
    private UUID department;
    private UUID position;
    private UUID manager;
    public EmployeeFixture name(String value) { name = value; return this; }
    public EmployeeFixture email(String value) { email = value; return this; }
    public EmployeeFixture cccd(String value) { cccd = value; return this; }
    public EmployeeFixture bank(String value) { bank = value; return this; }
    public EmployeeFixture department(UUID value) { department = value; return this; }
    public EmployeeFixture position(UUID value) { position = value; return this; }
    public EmployeeFixture manager(UUID value) { manager = value; return this; }
    public CreateEmployeeRequest build() {
        return new CreateEmployeeRequest(name, null, null, null, email, null, null,
                cccd, bank, department, position, manager, null);
    }
    public CreateEmployeeRequest complete() {
        return new CreateEmployeeRequest(name, LocalDate.of(1995, 5, 12), Gender.OTHER,
                "personal@example.test", email, "0900000000", "Địa chỉ giả lập",
                cccd, bank, department, position, manager, LocalDate.of(2026, 11, 17));
    }
    public static String identifier() {
        return "9" + String.format(java.util.Locale.ROOT, "%011d",
                Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000L));
    }
    public static String email() { return UUID.randomUUID() + "@example.test"; }
    public static String code() { return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(); }
}
