package com.elyares.etl.infrastructure.validator;

import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.validation.DataQualityReport;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.model.validation.ValidationResult;
import com.elyares.etl.domain.service.DataQualityService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Calcula métricas de calidad y las adjunta al resultado de validación.
 */
@Component
public class QualityValidator {

    private final DataQualityService dataQualityService = new DataQualityService();

    public ValidationResult enrich(List<RawRecord> originalRecords,
                                   ValidationResult validationResult,
                                   ValidationConfig config) {
        // QualityValidator no vuelve a decidir qué registros son válidos; solo calcula la foto
        // agregada del lote para thresholds y auditoría.
        DataQualityReport report = dataQualityService.evaluateQuality(
            originalRecords != null ? originalRecords.size() : 0,
            validationResult.getInvalidCount(),
            config.getErrorThreshold()
        );
        return ValidationResult.batch(
            validationResult.getValidRecords(),
            validationResult.getRejectedRecords(),
            validationResult.getErrors(),
            report
        );
    }
}
