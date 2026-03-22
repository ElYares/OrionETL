package com.elyares.etl.unit.rules;

import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.rules.NoDuplicateExecutionRule;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.shared.exception.ExecutionConflictException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoDuplicateExecutionRuleTest {

    @Test
    void shouldThrowWhenActiveExecutionExists() {
        ExecutionRepository repository = new ExecutionRepository() {
            @Override
            public PipelineExecution save(PipelineExecution execution) { return execution; }

            @Override
            public Optional<PipelineExecution> findByExecutionId(com.elyares.etl.domain.valueobject.ExecutionId executionId) {
                return Optional.empty();
            }

            @Override
            public Optional<PipelineExecution> findActiveByPipelineId(com.elyares.etl.domain.valueobject.PipelineId pipelineId) {
                PipelineExecution active = new PipelineExecution(
                    null, pipelineId, com.elyares.etl.domain.valueobject.ExecutionId.generate(),
                    com.elyares.etl.domain.enums.TriggerType.MANUAL, "tester"
                );
                return Optional.of(active);
            }

            @Override
            public List<PipelineExecution> findByPipelineId(com.elyares.etl.domain.valueobject.PipelineId pipelineId, int limit) {
                return List.of();
            }
        };

        NoDuplicateExecutionRule rule = new NoDuplicateExecutionRule(repository);

        assertThatThrownBy(() -> rule.evaluate(SampleDataFactory.aPipeline()))
            .isInstanceOf(ExecutionConflictException.class);
    }

    @Test
    void shouldAllowWhenNoActiveExecutionExists() {
        ExecutionRepository repository = new ExecutionRepository() {
            @Override
            public PipelineExecution save(PipelineExecution execution) { return execution; }

            @Override
            public Optional<PipelineExecution> findByExecutionId(com.elyares.etl.domain.valueobject.ExecutionId executionId) {
                return Optional.empty();
            }

            @Override
            public Optional<PipelineExecution> findActiveByPipelineId(com.elyares.etl.domain.valueobject.PipelineId pipelineId) {
                return Optional.empty();
            }

            @Override
            public List<PipelineExecution> findByPipelineId(com.elyares.etl.domain.valueobject.PipelineId pipelineId, int limit) {
                return List.of();
            }
        };

        NoDuplicateExecutionRule rule = new NoDuplicateExecutionRule(repository);

        assertThatCode(() -> rule.evaluate(SampleDataFactory.aPipeline())).doesNotThrowAnyException();
    }
}
