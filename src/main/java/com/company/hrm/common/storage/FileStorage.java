package com.company.hrm.common.storage;

public interface FileStorage {
    String store(byte[] content);
    byte[] read(String storedName);
    void delete(String storedName);
}
