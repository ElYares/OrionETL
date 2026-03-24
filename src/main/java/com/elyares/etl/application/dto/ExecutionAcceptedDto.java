package com.elyares.etl.application.dto;

import com.elyares.etl.domain.enums.ExecutionStatus;

import java.time.Instant;

/**
 * DTO devuelto cuando una ejecucion se acepta para procesamiento asincrono.
 */
public record ExecutionAcceptedDto(
    String executionId,
    String pipelineId,
    String pipelineName,
    ExecutionStatus status,
    Instant startedAt,
    String triggeredBy
) {}
