package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.usecase.loading.RegisterAuditUseCase;
import com.elyares.etl.domain.contract.AuditRepository;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.audit.AuditRecord;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.fixtures.SampleDataFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegisterAuditUseCaseTest {

    @Test
    void shouldCreateAndPersistAuditRecord() {
        var pipeline = SampleDataFactory.aPipeline();
        var execution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        Map<String, Object> details = Map.of("status", "SUCCESS");

        AuditRepository repository = mock(AuditRepository.class);
        when(repository.save(any(AuditRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterAuditUseCase useCase = new RegisterAuditUseCase(repository);

        AuditRecord result = useCase.execute(execution, pipeline, details);

        verify(repository, times(1)).save(any(AuditRecord.class));
        assertThat(result.getExecutionId()).isEqualTo(execution.getExecutionId());
        assertThat(result.getPipelineId()).isEqualTo(pipeline.getId());
        assertThat(result.getAction()).isEqualTo("EXECUTION_COMPLETED");
        assertThat(result.getDetails()).containsEntry("status", "SUCCESS");
    }
}
