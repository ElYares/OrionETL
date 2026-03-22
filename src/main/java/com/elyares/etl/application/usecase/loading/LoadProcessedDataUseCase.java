package com.elyares.etl.application.usecase.loading;

import com.elyares.etl.domain.contract.DataLoader;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.target.LoadResult;
import com.elyares.etl.domain.model.target.ProcessedRecord;

import java.util.List;

/**
 * Caso de uso para cargar registros procesados al destino final.
 */
public class LoadProcessedDataUseCase {

    private final DataLoader dataLoader;

    public LoadProcessedDataUseCase(DataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    public LoadResult execute(List<ProcessedRecord> processedRecords,
                              Pipeline pipeline,
                              PipelineExecution execution) {
        return dataLoader.load(processedRecords, pipeline.getTargetConfig(), execution);
    }
}
