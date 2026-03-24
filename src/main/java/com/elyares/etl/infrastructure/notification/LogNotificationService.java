package com.elyares.etl.infrastructure.notification;

import com.elyares.etl.domain.contract.ExecutionNotificationHook;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Canal V1 de notificación basado en logs estructurados.
 */
@Component
public class LogNotificationService implements ExecutionNotificationHook {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationService.class);

    @Override
    public void notifyFailure(PipelineExecution execution, ExecutionError error) {
        log.error(
            "etl_notification outcome=FAILED executionId={} pipelineId={} triggerType={} retryCount={} errorCode={} errorType={} step={} message={} totalRead={} totalRejected={} totalLoaded={}",
            execution.getExecutionId(),
            execution.getPipelineId(),
            execution.getTriggerType(),
            execution.getRetryCount(),
            error.getErrorCode(),
            error.getErrorType(),
            error.getStepName(),
            error.getMessage(),
            execution.getTotalRead().value(),
            execution.getTotalRejected().value(),
            execution.getTotalLoaded().value()
        );
    }

    @Override
    public void notifySuccess(PipelineExecution execution) {
        log.info(
            "etl_notification outcome=SUCCESS executionId={} pipelineId={} triggerType={} retryCount={} totalRead={} totalTransformed={} totalRejected={} totalLoaded={}",
            execution.getExecutionId(),
            execution.getPipelineId(),
            execution.getTriggerType(),
            execution.getRetryCount(),
            execution.getTotalRead().value(),
            execution.getTotalTransformed().value(),
            execution.getTotalRejected().value(),
            execution.getTotalLoaded().value()
        );
    }

    @Override
    public void notifyPartial(PipelineExecution execution, long rejectedCount) {
        log.warn(
            "etl_notification outcome=PARTIAL executionId={} pipelineId={} triggerType={} retryCount={} totalRead={} totalTransformed={} totalRejected={} totalLoaded={}",
            execution.getExecutionId(),
            execution.getPipelineId(),
            execution.getTriggerType(),
            execution.getRetryCount(),
            execution.getTotalRead().value(),
            execution.getTotalTransformed().value(),
            rejectedCount,
            execution.getTotalLoaded().value()
        );
    }
}
