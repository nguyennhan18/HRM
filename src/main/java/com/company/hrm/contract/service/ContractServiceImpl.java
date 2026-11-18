package com.company.hrm.contract.service;

import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.*;
import com.company.hrm.common.security.SensitiveFieldMasker;
import com.company.hrm.contract.dto.*;
import com.company.hrm.contract.entity.*;
import com.company.hrm.contract.mapper.ContractMapper;
import com.company.hrm.contract.repository.ContractRepository;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional(readOnly = true)
public class ContractServiceImpl implements ContractService {
    private final ContractRepository contracts;
    private final ContractEmployeeGuard employees;
    private final ContractAccess access;
    private final ContractMapper mapper;
    private final SensitiveFieldMasker masker;
    private final AuditService audit;
    private final Clock clock;
    public ContractServiceImpl(ContractRepository contracts, ContractEmployeeGuard employees,
            ContractAccess access, ContractMapper mapper, SensitiveFieldMasker masker,
            AuditService audit, Clock clock) {
        this.contracts = contracts; this.employees = employees; this.access = access;
        this.mapper = mapper; this.masker = masker; this.audit = audit; this.clock = clock;
    }
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContractResponse create(CreateContractRequest r, String traceId) {
        validateDates(r.type(), r.startDate(), r.endDate());
        employees.lockAndValidate(r.employeeId());
        var c = new Contract();
        c.revise(r.employeeId(), r.type(), r.startDate(), r.endDate(), r.baseSalary(), r.allowance());
        contracts.saveAndFlush(c);
        audit.record("CREATE", "Contract", c.getId(), null, snapshot(c), traceId);
        return response(c);
    }
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContractResponse update(UUID id, UpdateContractRequest r, String traceId) {
        var c = mutable(id);
        employees.lockAndValidate(c.getEmployeeId());
        if (!c.getEmployeeId().equals(r.employeeId())) employees.lockAndValidate(r.employeeId());
        validateDates(r.type(), r.startDate(), r.endDate());
        if (c.getStatus() == ContractStatus.ACTIVE && (!c.getEmployeeId().equals(r.employeeId())
                || c.getType() != r.type() || !c.getStartDate().equals(r.startDate())
                || extendsEnd(c.getEndDate(), r.endDate()))) {
            throw new BusinessException(ErrorCode.CONTRACT_ACTIVE_RESTRICTED,
                    "Hợp đồng đang hiệu lực chỉ được sửa lương, phụ cấp hoặc rút ngắn ngày kết thúc");
        }
        var before = snapshot(c);
        var changed = changedFields(c, r);
        c.revise(r.employeeId(), r.type(), r.startDate(), r.endDate(), r.baseSalary(), r.allowance());
        // DRAFT may overlap. ACTIVE can only shrink, but still validate the invariant at service level.
        if (c.getStatus() == ContractStatus.ACTIVE) checkOverlap(c);
        return save(c, "UPDATE", before, changed, traceId);
    }
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContractResponse activate(UUID id, String traceId) {
        var c = mutable(id);
        requireState(c, ContractStatus.DRAFT);
        employees.lockAndValidate(c.getEmployeeId());
        checkOverlap(c);
        var before = snapshot(c);
        c.activate();
        return save(c, "ACTIVATE", before, List.of("status"), traceId);
    }
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContractResponse terminate(UUID id, TerminateContractRequest r, String traceId) {
        var c = mutable(id);
        requireState(c, ContractStatus.ACTIVE);
        employees.lockAndValidate(c.getEmployeeId());
        if (r.endDate().isBefore(c.getStartDate()) || extendsEnd(c.getEndDate(), r.endDate()))
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Ngày chấm dứt ngoài thời hạn hợp đồng");
        var before = snapshot(c);
        c.terminate(r.endDate());
        return save(c, "TERMINATE", before, List.of("endDate", "status"), traceId);
    }
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void terminateActiveForEmployee(UUID employeeId, LocalDate terminationDate, String traceId) {
        var activeContracts = contracts.findByEmployeeIdAndStatusAndDeletedAtIsNull(employeeId, ContractStatus.ACTIVE);
        for (var c : activeContracts) {
            if (terminationDate.isBefore(c.getStartDate())) {
                throw new BusinessException(ErrorCode.HRM_CONFLICT,
                        "Ngày thôi việc không được trước ngày bắt đầu hợp đồng đang hiệu lực");
            }
        }
        for (var c : activeContracts) {
            var before = snapshot(c);
            LocalDate effectiveEnd = (c.getEndDate() == null || c.getEndDate().isAfter(terminationDate))
                    ? terminationDate : c.getEndDate();
            c.terminate(effectiveEnd);
            save(c, "TERMINATE", before, List.of("endDate", "status"), traceId);
        }
    }
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER','EMPLOYEE')")
    public ContractResponse get(UUID id) {
        var c = existing(id);
        if (!access.canRead(c.getEmployeeId())) throw new AccessDeniedException("Không có quyền xem hợp đồng");
        return response(c, access.canViewSalary(c.getEmployeeId()));
    }
    @PreAuthorize("@contractAccess.canRead(#employeeId)")
    public Page<ContractResponse> list(UUID employeeId, Pageable page) {
        boolean canViewSalary = access.canViewSalary(employeeId);
        return contracts.findByEmployeeIdAndDeletedAtIsNull(employeeId, checked(page))
                .map(c -> response(c, canViewSalary));
    }
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public Page<ContractResponse> expiring(int days, Pageable page) {
        if (days < 1 || days > 365)
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "days phải từ 1 đến 365");
        var today = today();
        return contracts.findByStatusAndDeletedAtIsNullAndEndDateBetween(ContractStatus.ACTIVE,
                today, today.plusDays(days), checked(page))
                .map(c -> response(c, access.canViewSalary(c.getEmployeeId())));
    }
    private ContractResponse save(Contract c, String action, Map<String,Object> before,
                                  List<String> changed, String traceId) {
        contracts.flush();
        var after = snapshot(c);
        after.put("changedFields", changed);
        audit.record(action, "Contract", c.getId(), before, after, traceId);
        return response(c);
    }
    private Contract existing(UUID id) {
        return contracts.findByIdAndDeletedAtIsNull(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy hợp đồng"));
    }
    private Contract mutable(UUID id) {
        var c = existing(id);
        if (c.getStatus() == ContractStatus.TERMINATED)
            throw new BusinessException(ErrorCode.CONTRACT_TERMINATED_IMMUTABLE, "Hợp đồng đã chấm dứt không được thay đổi");
        return c;
    }
    private void requireState(Contract c, ContractStatus state) {
        if (c.getStatus() != state)
            throw new BusinessException(ErrorCode.CONTRACT_INVALID_TRANSITION, "Chuyển trạng thái hợp đồng không hợp lệ");
    }
    private void checkOverlap(Contract c) {
        if (contracts.overlaps(c.getEmployeeId(), c.getId(), c.getStartDate(), c.getEndDate()))
            throw new BusinessException(ErrorCode.CONTRACT_OVERLAP, "Hiệu lực hợp đồng chồng lấn");
    }
    private boolean extendsEnd(LocalDate oldEnd, LocalDate newEnd) {
        return oldEnd != null && (newEnd == null || newEnd.isAfter(oldEnd));
    }
    private void validateDates(ContractType type, LocalDate start, LocalDate end) {
        if (end == null && type != ContractType.INDEFINITE || end != null && end.isBefore(start))
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Ngày kết thúc hợp đồng không hợp lệ");
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))); }
    private ContractResponse response(Contract c) {
        return response(c, access.canViewSalary(c.getEmployeeId()));
    }
    private ContractResponse response(Contract c, boolean canViewSalary) {
        return mapper.toResponse(c, c.getEndDate() != null && c.getEndDate().isBefore(today()))
                .masked(masker, canViewSalary);
    }
    private Map<String,Object> snapshot(Contract c) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("employeeId", c.getEmployeeId()); data.put("type", c.getType());
        data.put("startDate", c.getStartDate()); data.put("endDate", c.getEndDate());
        data.put("status", c.getStatus()); data.put("baseSalary", "***"); data.put("allowance", "***");
        return data;
    }
    private List<String> changedFields(Contract c, UpdateContractRequest r) {
        var fields = new ArrayList<String>();
        if (!c.getEmployeeId().equals(r.employeeId())) fields.add("employeeId");
        if (c.getType() != r.type()) fields.add("type");
        if (!c.getStartDate().equals(r.startDate())) fields.add("startDate");
        if (!Objects.equals(c.getEndDate(), r.endDate())) fields.add("endDate");
        if (c.getBaseSalary().compareTo(r.baseSalary()) != 0) fields.add("baseSalary");
        if (c.getAllowance().compareTo(r.allowance()) != 0) fields.add("allowance");
        return fields;
    }
    private Pageable checked(Pageable page) {
        var fields = Set.of("id", "startDate", "endDate", "status", "createdAt", "updatedAt");
        if (page.getSort().stream().anyMatch(o -> !fields.contains(o.getProperty())))
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Trường sắp xếp không hợp lệ");
        var sort = page.getSort().isSorted() ? page.getSort() : Sort.by("endDate");
        if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by("id"));
        return PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 200), sort);
    }
}
