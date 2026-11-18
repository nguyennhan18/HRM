package com.company.hrm.attendance.service;

import com.company.hrm.attendance.dto.CreateHolidayRequest;
import com.company.hrm.attendance.dto.HolidayResponse;
import com.company.hrm.attendance.dto.UpdateHolidayRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HolidayService {

    Page<HolidayResponse> list(LocalDate fromDate, LocalDate toDate, Pageable page);

    HolidayResponse get(UUID id);

    HolidayResponse create(@Valid CreateHolidayRequest request, String traceId);

    HolidayResponse update(UUID id, @Valid UpdateHolidayRequest request, String traceId);

    void delete(UUID id, String traceId);
}
