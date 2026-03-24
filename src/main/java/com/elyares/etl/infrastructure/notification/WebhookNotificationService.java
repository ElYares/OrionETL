package com.elyares.etl.infrastructure.notification;

import com.elyares.etl.domain.contract.ExecutionNotificationHook;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import org.springframework.stereotype.Component;

/**
 * Stub V2: el envío real por webhook se implementará en la siguiente versión.
 */
@Component
public class WebhookNotificationService implements ExecutionNotificationHook {

    @Override
    public void notifyFailure(PipelineExecution execution, ExecutionError error) {
        // V2
    }

    @Override
    public void notifySuccess(PipelineExecution execution) {
        // V2
    }

    @Override
    public void notifyPartial(PipelineExecution execution, long rejectedCount) {
        // V2
    }
}
