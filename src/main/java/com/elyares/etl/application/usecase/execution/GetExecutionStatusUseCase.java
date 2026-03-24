package com.elyares.etl.application.usecase.execution;

import com.elyares.etl.application.dto.ExecutionStatusDto;
import com.elyares.etl.application.mapper.ExecutionMapper;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.shared.exception.ExecutionNotFoundException;

/**
 * Caso de uso para consultar estado resumido de una ejecución.
 */
public class GetExecutionStatusUseCase {

    private final ExecutionRepository executionRepository;
    private final ExecutionMapper executionMapper;

    public GetExecutionStatusUseCase(ExecutionRepository executionRepository,
                                     ExecutionMapper executionMapper) {
        this.executionRepository = executionRepository;
        this.executionMapper = executionMapper;
    }

    public ExecutionStatusDto execute(String executionId) {
        return executionRepository.findByExecutionId(ExecutionId.of(executionId))
            .map(executionMapper::toStatusDto)
            .orElseThrow(() -> new ExecutionNotFoundException(executionId));
    }
}
