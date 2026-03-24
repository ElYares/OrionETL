package com.elyares.etl.interfaces.rest.controller;

import com.elyares.etl.application.dto.ExecutionAcceptedDto;
import com.elyares.etl.application.facade.PipelineExecutionFacade;
import com.elyares.etl.application.usecase.execution.ListExecutionsUseCase;
import com.elyares.etl.application.usecase.pipeline.GetPipelineUseCase;
import com.elyares.etl.application.usecase.pipeline.ListPipelinesUseCase;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.interfaces.rest.handler.GlobalExceptionHandler;
import com.elyares.etl.shared.exception.ExecutionConflictException;
import com.elyares.etl.shared.exception.PipelineNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PipelineController.class)
@Import(GlobalExceptionHandler.class)
class PipelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListPipelinesUseCase listPipelinesUseCase;

    @MockBean
    private GetPipelineUseCase getPipelineUseCase;

    @MockBean
    private ListExecutionsUseCase listExecutionsUseCase;

    @MockBean
    private PipelineExecutionFacade pipelineExecutionFacade;

    @Test
    void executeShouldReturnAccepted() throws Exception {
        when(pipelineExecutionFacade.trigger(eq("sales-daily"), eq("api:test"), eq(Map.of("batch_date", "2026-03-23"))))
            .thenReturn(new ExecutionAcceptedDto(
                "01234567-89ab-cdef-0123-456789abcdef",
                "9b4d1aa8-e5f2-4e38-b3cc-aeb89d3ab001",
                "sales-daily",
                ExecutionStatus.RUNNING,
                Instant.parse("2026-03-23T10:00:00Z"),
                "api:test"
            ));

        mockMvc.perform(post("/api/v1/pipelines/sales-daily/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggeredBy": "api:test",
                      "parameters": {
                        "batch_date": "2026-03-23"
                      }
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.executionId").value("01234567-89ab-cdef-0123-456789abcdef"))
            .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    @Test
    void executeShouldReturnConflictWhenExecutionAlreadyRunning() throws Exception {
        when(pipelineExecutionFacade.trigger(eq("sales-daily"), eq("api:test"), eq(Map.of())))
            .thenThrow(new ExecutionConflictException("sales-daily"));

        mockMvc.perform(post("/api/v1/pipelines/sales-daily/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggeredBy": "api:test",
                      "parameters": {}
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("ETL_EXEC_CONFLICT"));
    }

    @Test
    void executeShouldReturnNotFoundForUnknownPipeline() throws Exception {
        when(pipelineExecutionFacade.trigger(eq("unknown-pipeline"), eq("api:test"), eq(Map.of())))
            .thenThrow(new PipelineNotFoundException("unknown-pipeline"));

        mockMvc.perform(post("/api/v1/pipelines/unknown-pipeline/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggeredBy": "api:test",
                      "parameters": {}
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ETL_PIPELINE_NOT_FOUND"));
    }
}
