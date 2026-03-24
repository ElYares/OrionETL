package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.mapper.ExecutionMapper;
import com.elyares.etl.application.usecase.execution.RetryExecutionUseCase;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.rules.RetryEligibilityRule;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.shared.exception.EtlException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetryExecutionUseCaseTest {

    @Test
    void shouldCreateRetryExecutionAndReturnDto() {
        var pipeline = SampleDataFactory.aPipeline();
        var failedExecution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        failedExecution.addError(new ExecutionError(
            java.util.UUID.randomUUID(),
            failedExecution.getExecutionId(),
            "EXTRACT",
            ErrorType.TECHNICAL,
            com.elyares.etl.domain.enums.ErrorSeverity.ERROR,
            "ERR-1",
            "failure",
            null,
            null
        ));

        PipelineExecution retryExecution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.RETRY,
            "retry-user"
        );

        PipelineExecutionDto dto = new PipelineExecutionDto(
            retryExecution.getExecutionId().toString(),
            pipeline.getId().toString(),
            pipeline.getName(),
            ExecutionStatus.PENDING,
            TriggerType.RETRY,
            "retry-user",
            Instant.now(),
            null,
            0,
            0,
            0,
            0,
            null,
            List.of()
        );

        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        PipelineRepository pipelineRepository = mock(PipelineRepository.class);
        RetryEligibilityRule retryEligibilityRule = mock(RetryEligibilityRule.class);
        ExecutionLifecycleService lifecycleService = mock(ExecutionLifecycleService.class);
        ExecutionMapper executionMapper = mock(ExecutionMapper.class);

        when(executionRepository.findByExecutionId(failedExecution.getExecutionId())).thenReturn(Optional.of(failedExecution));
        when(pipelineRepository.findById(pipeline.getId())).thenReturn(Optional.of(pipeline));
        when(lifecycleService.createExecution(pipeline.getId(), TriggerType.RETRY, "retry-user")).thenReturn(retryExecution);
        when(executionRepository.save(any(PipelineExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionMapper.toDto(retryExecution, pipeline.getName())).thenReturn(dto);

        RetryExecutionUseCase useCase = new RetryExecutionUseCase(
            executionRepository,
            pipelineRepository,
            retryEligibilityRule,
            lifecycleService,
            executionMapper
        );

        PipelineExecutionDto result = useCase.execute(failedExecution.getExecutionId().toString(), "retry-user");

        verify(retryEligibilityRule, times(1))
            .evaluate(eq(failedExecution.getRetryCount()), eq(ErrorType.TECHNICAL), eq(pipeline.getRetryPolicy()));
        verify(executionRepository, times(1)).save(retryExecution);
        assertThat(retryExecution.getParentExecutionId()).isEqualTo(failedExecution.getId());
        assertThat(retryExecution.getRetryCount()).isEqualTo(1);
        assertThat(result).isEqualTo(dto);
    }

    @Test
    void shouldIncrementRetryCountFromPreviousAttempt() {
        var pipeline = SampleDataFactory.aPipeline();
        var failedExecution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.RETRY,
            "tester"
        );
        failedExecution.incrementRetryCount();
        failedExecution.addError(new ExecutionError(
            java.util.UUID.randomUUID(),
            failedExecution.getExecutionId(),
            "LOAD",
            ErrorType.TECHNICAL,
            com.elyares.etl.domain.enums.ErrorSeverity.ERROR,
            "ERR-2",
            "failure",
            null,
            null
        ));

        PipelineExecution retryExecution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.RETRY,
            "retry-user"
        );

        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        PipelineRepository pipelineRepository = mock(PipelineRepository.class);
        RetryEligibilityRule retryEligibilityRule = mock(RetryEligibilityRule.class);
        ExecutionLifecycleService lifecycleService = mock(ExecutionLifecycleService.class);
        ExecutionMapper executionMapper = mock(ExecutionMapper.class);

        when(executionRepository.findByExecutionId(failedExecution.getExecutionId())).thenReturn(Optional.of(failedExecution));
        when(pipelineRepository.findById(pipeline.getId())).thenReturn(Optional.of(pipeline));
        when(lifecycleService.createExecution(pipeline.getId(), TriggerType.RETRY, "retry-user")).thenReturn(retryExecution);
        when(executionRepository.save(any(PipelineExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RetryExecutionUseCase useCase = new RetryExecutionUseCase(
            executionRepository,
            pipelineRepository,
            retryEligibilityRule,
            lifecycleService,
            executionMapper
        );

        PipelineExecution createdRetry = useCase.createRetryExecution(
            failedExecution.getExecutionId().toString(),
            "retry-user"
        );

        assertThat(createdRetry.getRetryCount()).isEqualTo(2);
        assertThat(createdRetry.getParentExecutionId()).isEqualTo(failedExecution.getId());
    }

    @Test
    void shouldThrowWhenFailedExecutionNotFound() {
        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        PipelineRepository pipelineRepository = mock(PipelineRepository.class);
        RetryEligibilityRule retryEligibilityRule = mock(RetryEligibilityRule.class);
        ExecutionLifecycleService lifecycleService = mock(ExecutionLifecycleService.class);
        ExecutionMapper executionMapper = mock(ExecutionMapper.class);
        when(executionRepository.findByExecutionId(any())).thenReturn(Optional.empty());

        RetryExecutionUseCase useCase = new RetryExecutionUseCase(
            executionRepository,
            pipelineRepository,
            retryEligibilityRule,
            lifecycleService,
            executionMapper
        );

        assertThatThrownBy(() -> useCase.execute(SampleDataFactory.anExecutionId().toString(), "user"))
            .isInstanceOf(EtlException.class)
            .hasMessageContaining("Execution not found");
    }

    @Test
    void shouldThrowWhenPipelineNotFoundForFailedExecution() {
        var failedExecution = new PipelineExecution(
            null,
            SampleDataFactory.aPipelineId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );

        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        PipelineRepository pipelineRepository = mock(PipelineRepository.class);
        RetryEligibilityRule retryEligibilityRule = mock(RetryEligibilityRule.class);
        ExecutionLifecycleService lifecycleService = mock(ExecutionLifecycleService.class);
        ExecutionMapper executionMapper = mock(ExecutionMapper.class);
        when(executionRepository.findByExecutionId(failedExecution.getExecutionId())).thenReturn(Optional.of(failedExecution));
        when(pipelineRepository.findById(failedExecution.getPipelineId())).thenReturn(Optional.empty());

        RetryExecutionUseCase useCase = new RetryExecutionUseCase(
            executionRepository,
            pipelineRepository,
            retryEligibilityRule,
            lifecycleService,
            executionMapper
        );

        assertThatThrownBy(() -> useCase.execute(failedExecution.getExecutionId().toString(), "user"))
            .isInstanceOf(EtlException.class)
            .hasMessageContaining("Pipeline not found");
    }
}
