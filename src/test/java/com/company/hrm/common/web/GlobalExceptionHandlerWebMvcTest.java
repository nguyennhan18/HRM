package com.company.hrm.common.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.hrm.common.config.TimeConfig;
import com.company.hrm.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ErrorHandlingTestController.class)
@Import({GlobalExceptionHandler.class, TimeConfig.class})
class GlobalExceptionHandlerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_return_field_errors_when_validation_fails() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("HRM_VALIDATION_ERROR")))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field", is("name")));
    }

    @Test
    void should_return_not_found_when_resource_is_missing() throws Exception {
        mockMvc.perform(get("/test/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("HRM_RESOURCE_NOT_FOUND")));
    }

    @Test
    void should_return_conflict_when_business_rule_conflicts() throws Exception {
        mockMvc.perform(get("/test/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("HRM_CONFLICT")));
    }

    @Test
    void should_return_unprocessable_entity_when_business_rule_is_invalid() throws Exception {
        mockMvc.perform(get("/test/errors/unprocessable"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("HRM_UNPROCESSABLE_ENTITY")));
    }

    @Test
    void should_hide_internal_details_when_unexpected_error_occurs() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected").header("X-Correlation-ID", "test-trace-id"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is("HRM_INTERNAL_ERROR")))
                .andExpect(jsonPath("$.traceId", is("test-trace-id")))
                .andExpect(jsonPath("$.detail").value("Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau."))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
    }
}
