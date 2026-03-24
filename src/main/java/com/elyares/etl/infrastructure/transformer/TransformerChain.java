package com.elyares.etl.infrastructure.transformer;

import com.elyares.etl.domain.contract.DataTransformer;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.transformation.TransformationResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cadena de transformadores. Hoy arranca con el transformador común.
 */
@Component
public class TransformerChain implements DataTransformer {

    private final CommonTransformer commonTransformer;

    public TransformerChain(CommonTransformer commonTransformer) {
        this.commonTransformer = commonTransformer;
    }

    @Override
    public TransformationResult transform(List<RawRecord> records, Pipeline pipeline, PipelineExecution execution) {
        // Por ahora la cadena arranca y termina en CommonTransformer; la estructura ya queda
        // lista para insertar transformadores específicos por pipeline en fases siguientes.
        CommonTransformer.CommonTransformationOutput output = commonTransformer.transform(records, pipeline);
        return TransformationResult.of(output.processedRecords(), output.rejectedRecords());
    }

    @Override
    public String getPipelineName() {
        return "*";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
