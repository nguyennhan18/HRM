package com.company.hrm.employee.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;

@Entity @Table(name = "employee_documents") @Getter
public class EmployeeDocument extends BaseEntity {
    @Column(nullable=false) private UUID employeeId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private DocumentType type;
    @Column(nullable=false, length=255) private String originalName;
    @Column(nullable=false, length=36) private String storedName;
    @Column(nullable=false) private long size;
    @Column(nullable=false, length=50) private String contentType;
    @Column(nullable=false, length=64) private String checksum;
    protected EmployeeDocument() { }
    public EmployeeDocument(UUID employeeId, DocumentType type, String originalName, String storedName,
                            long size, String contentType, String checksum) {
        this.employeeId=employeeId; this.type=type; this.originalName=originalName; this.storedName=storedName;
        this.size=size; this.contentType=contentType; this.checksum=checksum;
    }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof EmployeeDocument d && getId()!=null && getId().equals(d.getId());
    }
    @Override public int hashCode() { return EmployeeDocument.class.hashCode(); }
}
