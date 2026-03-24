package com.elyares.etl.interfaces.rest.request;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request HTTP para disparar una ejecucion manual de pipeline.
 */
public record ExecutePipelineRequest(
    @Size(max = 200, message = "triggeredBy must be at most 200 characters")
    String triggeredBy,
    Map<String, String> parameters
) {}
