package com.company.hrm.attendance.service;

import com.company.hrm.attendance.dto.CreateHolidayRequest;
import com.company.hrm.attendance.dto.HolidayResponse;
import com.company.hrm.attendance.dto.UpdateHolidayRequest;
import com.company.hrm.attendance.entity.Holiday;
import com.company.hrm.attendance.mapper.HolidayMapper;
import com.company.hrm.attendance.repository.HolidayRepository;
import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class HolidayServiceImpl implements HolidayService {

    private final HolidayRepository holidays;
    private final HolidayMapper mapper;
    private final AuditService audit;

    public HolidayServiceImpl(HolidayRepository holidays, HolidayMapper mapper, AuditService audit) {
        this.holidays = holidays;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public Page<HolidayResponse> list(LocalDate fromDate, LocalDate toDate, Pageable page) {
        var checked = AttendancePages.holidays(page);
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Ngày kết thúc lọc không được trước ngày bắt đầu");
        }
        Page<Holiday> result;
        if (fromDate != null && toDate != null) {
            result = holidays.findByHolidayDateBetween(fromDate, toDate, checked);
        } else if (fromDate != null) {
            result = holidays.findByHolidayDateGreaterThanEqual(fromDate, checked);
        } else if (toDate != null) {
            result = holidays.findByHolidayDateLessThanEqual(toDate, checked);
        } else {
            result = holidays.findAll(checked);
        }
        return result.map(mapper::toResponse);
    }

    @Override
    public HolidayResponse get(UUID id) {
        return mapper.toResponse(existing(id));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HolidayResponse create(CreateHolidayRequest request, String traceId) {
        if (holidays.existsByHolidayDate(request.holidayDate())) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Ngày lễ đã tồn tại");
        }
        var entity = new Holiday(request.holidayDate(), request.name(), request.multiplier());
        holidays.saveAndFlush(entity);
        var after = mapper.toResponse(entity);
        audit.record("CREATE", "Holiday", entity.getId(), null, after, traceId);
        return after;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HolidayResponse update(UUID id, UpdateHolidayRequest request, String traceId) {
        var entity = locked(id);
        if (holidays.existsByHolidayDateAndIdNot(request.holidayDate(), id)) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Ngày lễ đã tồn tại");
        }
        var before = mapper.toResponse(entity);
        entity.update(request.holidayDate(), request.name(), request.multiplier());
        holidays.flush();
        var after = mapper.toResponse(entity);
        audit.record("UPDATE", "Holiday", id, before, after, traceId);
        return after;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(UUID id, String traceId) {
        var entity = locked(id);
        var before = mapper.toResponse(entity);
        holidays.delete(entity);
        holidays.flush();
        audit.record("DELETE", "Holiday", id, before, null, traceId);
    }

    private Holiday existing(UUID id) {
        return holidays.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy ngày lễ"));
    }

    private Holiday locked(UUID id) {
        return holidays.lockById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy ngày lễ"));
    }
}
