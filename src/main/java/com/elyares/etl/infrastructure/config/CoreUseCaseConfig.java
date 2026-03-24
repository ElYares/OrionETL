package com.elyares.etl.infrastructure.config;

import com.elyares.etl.application.mapper.ExecutionMapper;
import com.elyares.etl.application.mapper.PipelineMapper;
import com.elyares.etl.application.orchestrator.ETLOrchestrator;
import com.elyares.etl.application.facade.ExecutionMonitoringFacade;
import com.elyares.etl.application.facade.PipelineExecutionFacade;
import com.elyares.etl.application.usecase.execution.ExecutePipelineUseCase;
import com.elyares.etl.application.usecase.execution.GetExecutionStatusUseCase;
import com.elyares.etl.application.usecase.execution.ListExecutionsUseCase;
import com.elyares.etl.application.usecase.execution.PipelineExecutionRunner;
import com.elyares.etl.application.usecase.execution.RetryExecutionUseCase;
import com.elyares.etl.application.usecase.extraction.ExtractDataUseCase;
import com.elyares.etl.application.usecase.loading.LoadProcessedDataUseCase;
import com.elyares.etl.application.usecase.loading.PersistRejectedRecordsUseCase;
import com.elyares.etl.application.usecase.loading.RegisterAuditUseCase;
import com.elyares.etl.application.usecase.pipeline.GetPipelineUseCase;
import com.elyares.etl.application.usecase.pipeline.ListPipelinesUseCase;
import com.elyares.etl.application.usecase.pipeline.ResolvePipelineConfigUseCase;
import com.elyares.etl.domain.contract.AuditRepository;
import com.elyares.etl.domain.contract.DataLoader;
import com.elyares.etl.domain.contract.ExecutionNotificationHook;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.contract.RejectedRecordRepository;
import com.elyares.etl.domain.rules.AllowedExecutionWindowRule;
import com.elyares.etl.domain.rules.CriticalErrorBlocksSuccessRule;
import com.elyares.etl.domain.rules.NoDuplicateExecutionRule;
import com.elyares.etl.domain.rules.RetryEligibilityRule;
import com.elyares.etl.domain.service.DataQualityService;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import com.elyares.etl.domain.service.PipelineOrchestrationService;
import com.elyares.etl.infrastructure.extractor.ExtractorRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

/**
 * Wiring central de servicios de dominio, casos de uso y orquestación end-to-end.
 */
@Configuration
public class CoreUseCaseConfig {

    @Bean
    GetPipelineUseCase getPipelineUseCase(PipelineRepository pipelineRepository, PipelineMapper pipelineMapper) {
        return new GetPipelineUseCase(pipelineRepository, pipelineMapper);
    }

    @Bean
    ListPipelinesUseCase listPipelinesUseCase(PipelineRepository pipelineRepository, PipelineMapper pipelineMapper) {
        return new ListPipelinesUseCase(pipelineRepository, pipelineMapper);
    }

    @Bean
    ResolvePipelineConfigUseCase resolvePipelineConfigUseCase(GetPipelineUseCase getPipelineUseCase) {
        return new ResolvePipelineConfigUseCase(getPipelineUseCase);
    }

    @Bean
    NoDuplicateExecutionRule noDuplicateExecutionRule(ExecutionRepository executionRepository) {
        return new NoDuplicateExecutionRule(executionRepository);
    }

    @Bean
    AllowedExecutionWindowRule allowedExecutionWindowRule() {
        return new AllowedExecutionWindowRule();
    }

    @Bean
    CriticalErrorBlocksSuccessRule criticalErrorBlocksSuccessRule() {
        return new CriticalErrorBlocksSuccessRule();
    }

    @Bean
    RetryEligibilityRule retryEligibilityRule() {
        return new RetryEligibilityRule();
    }

    @Bean
    PipelineOrchestrationService pipelineOrchestrationService(NoDuplicateExecutionRule noDuplicateExecutionRule,
                                                             AllowedExecutionWindowRule allowedExecutionWindowRule,
                                                             CriticalErrorBlocksSuccessRule criticalErrorBlocksSuccessRule,
                                                             RetryEligibilityRule retryEligibilityRule) {
        return new PipelineOrchestrationService(
            noDuplicateExecutionRule,
            allowedExecutionWindowRule,
            criticalErrorBlocksSuccessRule,
            retryEligibilityRule
        );
    }

    @Bean
    ExecutionLifecycleService executionLifecycleService(ExecutionRepository executionRepository) {
        return new ExecutionLifecycleService(executionRepository);
    }

    @Bean
    DataQualityService dataQualityService() {
        return new DataQualityService();
    }

    @Bean
    ExtractDataUseCase extractDataUseCase(ExtractorRegistry extractorRegistry) {
        return new ExtractDataUseCase(extractorRegistry);
    }

    @Bean
    LoadProcessedDataUseCase loadProcessedDataUseCase(DataLoader dataLoader) {
        return new LoadProcessedDataUseCase(dataLoader);
    }

    @Bean
    PersistRejectedRecordsUseCase persistRejectedRecordsUseCase(RejectedRecordRepository rejectedRecordRepository) {
        return new PersistRejectedRecordsUseCase(rejectedRecordRepository);
    }

    @Bean
    RegisterAuditUseCase registerAuditUseCase(AuditRepository auditRepository) {
        return new RegisterAuditUseCase(auditRepository);
    }

    @Bean
    ETLOrchestrator etlOrchestrator(ExtractDataUseCase extractDataUseCase,
                                    com.elyares.etl.application.usecase.validation.ValidateInputDataUseCase validateInputDataUseCase,
                                    com.elyares.etl.application.usecase.transformation.TransformDataUseCase transformDataUseCase,
                                    com.elyares.etl.application.usecase.validation.ValidateBusinessDataUseCase validateBusinessDataUseCase,
                                    LoadProcessedDataUseCase loadProcessedDataUseCase,
                                    PersistRejectedRecordsUseCase persistRejectedRecordsUseCase,
                                    RegisterAuditUseCase registerAuditUseCase,
                                    ExecutionLifecycleService executionLifecycleService,
                                    PipelineOrchestrationService pipelineOrchestrationService,
                                    DataQualityService dataQualityService,
                                    java.util.List<ExecutionNotificationHook> executionNotificationHooks) {
        return new ETLOrchestrator(
            extractDataUseCase,
            validateInputDataUseCase,
            transformDataUseCase,
            validateBusinessDataUseCase,
            loadProcessedDataUseCase,
            persistRejectedRecordsUseCase,
            registerAuditUseCase,
            executionLifecycleService,
            pipelineOrchestrationService,
            dataQualityService,
            executionNotificationHooks
        );
    }

    @Bean
    PipelineExecutionRunner pipelineExecutionRunner(ExecutionLifecycleService executionLifecycleService,
                                                    ETLOrchestrator etlOrchestrator,
                                                    RetryExecutionUseCase retryExecutionUseCase) {
        return new PipelineExecutionRunner(executionLifecycleService, etlOrchestrator, retryExecutionUseCase);
    }

    @Bean
    ExecutePipelineUseCase executePipelineUseCase(GetPipelineUseCase getPipelineUseCase,
                                                  PipelineOrchestrationService pipelineOrchestrationService,
                                                  PipelineExecutionRunner pipelineExecutionRunner,
                                                  ExecutionMapper executionMapper) {
        return new ExecutePipelineUseCase(
            getPipelineUseCase,
            pipelineOrchestrationService,
            pipelineExecutionRunner,
            executionMapper
        );
    }

    @Bean
    GetExecutionStatusUseCase getExecutionStatusUseCase(ExecutionRepository executionRepository,
                                                        ExecutionMapper executionMapper) {
        return new GetExecutionStatusUseCase(executionRepository, executionMapper);
    }

    @Bean
    ListExecutionsUseCase listExecutionsUseCase(ExecutionRepository executionRepository,
                                                PipelineRepository pipelineRepository,
                                                ExecutionMapper executionMapper) {
        return new ListExecutionsUseCase(executionRepository, pipelineRepository, executionMapper);
    }

    @Bean
    RetryExecutionUseCase retryExecutionUseCase(ExecutionRepository executionRepository,
                                                PipelineRepository pipelineRepository,
                                                RetryEligibilityRule retryEligibilityRule,
                                                ExecutionLifecycleService executionLifecycleService,
                                                ExecutionMapper executionMapper) {
        return new RetryExecutionUseCase(
            executionRepository,
            pipelineRepository,
            retryEligibilityRule,
            executionLifecycleService,
            executionMapper
        );
    }

    @Bean
    PipelineExecutionFacade pipelineExecutionFacade(GetPipelineUseCase getPipelineUseCase,
                                                    PipelineOrchestrationService pipelineOrchestrationService,
                                                    ExecutionLifecycleService executionLifecycleService,
                                                    PipelineExecutionRunner pipelineExecutionRunner,
                                                    TaskExecutor taskExecutor) {
        return new PipelineExecutionFacade(
            getPipelineUseCase,
            pipelineOrchestrationService,
            executionLifecycleService,
            pipelineExecutionRunner,
            taskExecutor
        );
    }

    @Bean
    ExecutionMonitoringFacade executionMonitoringFacade(ExecutionRepository executionRepository,
                                                        PipelineRepository pipelineRepository,
                                                        RejectedRecordRepository rejectedRecordRepository,
                                                        ExecutionMapper executionMapper) {
        return new ExecutionMonitoringFacade(
            executionRepository,
            pipelineRepository,
            rejectedRecordRepository,
            executionMapper
        );
    }
}
