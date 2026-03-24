package com.elyares.etl.domain.contract;

import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;

/**
 * Puerto de salida para notificaciones operativas al cerrar una ejecución.
 */
public interface ExecutionNotificationHook {

    void notifyFailure(PipelineExecution execution, ExecutionError error);

    void notifySuccess(PipelineExecution execution);

    void notifyPartial(PipelineExecution execution, long rejectedCount);
}
