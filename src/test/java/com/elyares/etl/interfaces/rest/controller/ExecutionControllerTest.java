package com.elyares.etl.interfaces.rest.controller;

import com.elyares.etl.application.dto.ExecutionStepDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.dto.RejectedRecordDto;
import com.elyares.etl.application.facade.ExecutionMonitoringFacade;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.StepStatus;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.interfaces.rest.handler.GlobalExceptionHandler;
import com.elyares.etl.shared.response.PagedResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutionController.class)
@Import(GlobalExceptionHandler.class)
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExecutionMonitoringFacade executionMonitoringFacade;

    @Test
    void getExecutionShouldReturnFullStatusWithSteps() throws Exception {
        when(executionMonitoringFacade.getExecution("01234567-89ab-cdef-0123-456789abcdef"))
            .thenReturn(new PipelineExecutionDto(
                "01234567-89ab-cdef-0123-456789abcdef",
                "9b4d1aa8-e5f2-4e38-b3cc-aeb89d3ab001",
                "sales-daily",
                ExecutionStatus.RUNNING,
                TriggerType.MANUAL,
                "api:test",
                Instant.parse("2026-03-23T10:00:00Z"),
                null,
                10,
                9,
                1,
                0,
                null,
                List.of(new ExecutionStepDto("EXTRACT", 2, StepStatus.SUCCESS,
                    Instant.parse("2026-03-23T10:00:01Z"), Instant.parse("2026-03-23T10:00:03Z"), 10L, null))
            ));

        mockMvc.perform(get("/api/v1/executions/01234567-89ab-cdef-0123-456789abcdef"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.executionId").value("01234567-89ab-cdef-0123-456789abcdef"))
            .andExpect(jsonPath("$.data.steps[0].stepName").value("EXTRACT"));
    }

    @Test
    void getRejectedShouldReturnPagedRecords() throws Exception {
        when(executionMonitoringFacade.getRejectedRecords("01234567-89ab-cdef-0123-456789abcdef", 0, 2))
            .thenReturn(PagedResponse.of(List.of(
                new RejectedRecordDto(
                    "01234567-89ab-cdef-0123-456789abcdef",
                    "VALIDATE_SCHEMA",
                    15L,
                    Map.of("transaction_id", "A-15"),
                    "missing amount",
                    Instant.parse("2026-03-23T10:01:00Z")
                )
            ), 0, 2, 1));

        mockMvc.perform(get("/api/v1/executions/01234567-89ab-cdef-0123-456789abcdef/rejected?page=0&size=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].rowNumber").value(15))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
