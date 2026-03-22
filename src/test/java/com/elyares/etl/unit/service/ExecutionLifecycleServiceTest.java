package com.elyares.etl.unit.service;

import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.shared.exception.EtlException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionLifecycleServiceTest {

    @Test
    void shouldCreateAndMarkSuccessExecution() {
        InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        ExecutionLifecycleService service = new ExecutionLifecycleService(repository);

        PipelineExecution created = service.createExecution(PipelineId.generate(), TriggerType.MANUAL, "tester");
        PipelineExecution running = service.markRunning(created.getExecutionId());
        PipelineExecution success = service.markSuccess(running.getExecutionId(), 10, 8, 2, 8);

        assertThat(success.getStatus().name()).isEqualTo("SUCCESS");
        assertThat(success.getTotalRead().value()).isEqualTo(10);
        assertThat(success.getTotalLoaded().value()).isEqualTo(8);
    }

    @Test
    void shouldCloseNonTerminalExecutionAsFailed() {
        InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        ExecutionLifecycleService service = new ExecutionLifecycleService(repository);

        PipelineExecution created = service.createExecution(PipelineId.generate(), TriggerType.MANUAL, "tester");
        service.markRunning(created.getExecutionId());
        service.closeExecution(created.getExecutionId());

        PipelineExecution loaded = repository.findByExecutionId(created.getExecutionId()).orElseThrow();
        assertThat(loaded.getStatus().name()).isEqualTo("FAILED");
    }

    @Test
    void shouldMarkExecutionAsPartial() {
        InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        ExecutionLifecycleService service = new ExecutionLifecycleService(repository);

        PipelineExecution created = service.createExecution(PipelineId.generate(), TriggerType.MANUAL, "tester");
        service.markRunning(created.getExecutionId());
        PipelineExecution partial = service.markPartial(created.getExecutionId(), 10, 8, 2, 8, "Partial");

        assertThat(partial.getStatus().name()).isEqualTo("PARTIAL");
        assertThat(partial.getTotalRejected().value()).isEqualTo(2);
        assertThat(partial.getErrorSummary()).isEqualTo("Partial");
    }

    @Test
    void shouldThrowWhenExecutionDoesNotExist() {
        InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        ExecutionLifecycleService service = new ExecutionLifecycleService(repository);

        assertThatThrownBy(() -> service.markFailed(ExecutionId.generate(), "missing"))
            .isInstanceOf(EtlException.class)
            .hasMessageContaining("Execution not found");
    }

    private static final class InMemoryExecutionRepository implements ExecutionRepository {
        private final Map<ExecutionId, PipelineExecution> storage = new HashMap<>();

        @Override
        public PipelineExecution save(PipelineExecution execution) {
            storage.put(execution.getExecutionId(), execution);
            return execution;
        }

        @Override
        public Optional<PipelineExecution> findByExecutionId(ExecutionId executionId) {
            return Optional.ofNullable(storage.get(executionId));
        }

        @Override
        public Optional<PipelineExecution> findActiveByPipelineId(PipelineId pipelineId) {
            return storage.values().stream()
                .filter(e -> e.getPipelineId().equals(pipelineId) && e.getStatus().isActive())
                .findFirst();
        }

        @Override
        public List<PipelineExecution> findByPipelineId(PipelineId pipelineId, int limit) {
            return storage.values().stream()
                .filter(e -> e.getPipelineId().equals(pipelineId))
                .limit(limit)
                .toList();
        }
    }
}
