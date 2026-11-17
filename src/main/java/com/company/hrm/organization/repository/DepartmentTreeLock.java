package com.company.hrm.organization.repository;

import com.company.hrm.common.exception.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DepartmentTreeLock {
    private final JdbcTemplate jdbc;
    public DepartmentTreeLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // UNION (not UNION ALL) also terminates if legacy data contains a cycle.
    private static final String CHAIN = """
        WITH RECURSIVE ancestors AS (
            SELECT id, parent_id FROM departments WHERE id = ? OR id = ?
            UNION
            SELECT d.id, d.parent_id FROM departments d JOIN ancestors a ON d.id = a.parent_id
        )
        """;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndValidate(UUID id, UUID parentId) {
        try {
            var locked = jdbc.queryForList(CHAIN + """
                SELECT d.id FROM departments d JOIN ancestors a ON d.id = a.id
                ORDER BY d.id FOR UPDATE OF d
                """, UUID.class, id, parentId);
            // READ COMMITTED gets a fresh snapshot after a lock wait. Never validate
            // using the CTE snapshot taken before the competing transaction committed.
            var current = jdbc.queryForList(CHAIN + "SELECT id FROM ancestors", UUID.class, id, parentId);
            if (!new HashSet<>(locked).containsAll(current)) {
                throw conflict("Cây phòng ban vừa thay đổi, vui lòng thử lại");
            }
            if (parentId != null && Boolean.FALSE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM departments WHERE id = ? AND deleted_at IS NULL)",
                    Boolean.class, parentId))) {
                throw new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy phòng ban cha");
            }
            var parents = jdbc.queryForList(CHAIN + "SELECT id FROM ancestors", UUID.class, parentId, parentId);
            if (id != null && parents.contains(id)) {
                throw conflict("Không được tạo vòng lặp phòng ban");
            }
        } catch (PessimisticLockingFailureException ex) {
            throw conflict("Cây phòng ban đang được cập nhật, vui lòng thử lại");
        }
    }
    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.HRM_CONFLICT, message);
    }
}
