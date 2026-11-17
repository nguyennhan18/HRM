package com.company.hrm.employee.dto;
public record DocumentDownload(String originalName, String contentType, byte[] content) {
    @Override public String toString() { return "DocumentDownload[content=***]"; }
}
