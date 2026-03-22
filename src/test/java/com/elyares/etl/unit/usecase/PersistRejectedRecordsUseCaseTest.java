package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.usecase.loading.PersistRejectedRecordsUseCase;
import com.elyares.etl.domain.contract.RejectedRecordRepository;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.fixtures.SampleDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class PersistRejectedRecordsUseCaseTest {

    @Test
    void shouldPersistRejectedRecordsWhenListHasData() {
        RejectedRecordRepository repository = mock(RejectedRecordRepository.class);
        PersistRejectedRecordsUseCase useCase = new PersistRejectedRecordsUseCase(repository);
        RejectedRecord rejected = new RejectedRecord(
            SampleDataFactory.aRawRecord(),
            "VALIDATE_SCHEMA",
            "invalid",
            List.of()
        );
        var executionId = SampleDataFactory.anExecutionId();

        useCase.execute(List.of(rejected), executionId);

        verify(repository, times(1)).saveAll(List.of(rejected), executionId);
    }

    @Test
    void shouldSkipPersistenceWhenListIsNullOrEmpty() {
        RejectedRecordRepository repository = mock(RejectedRecordRepository.class);
        PersistRejectedRecordsUseCase useCase = new PersistRejectedRecordsUseCase(repository);

        useCase.execute(null, SampleDataFactory.anExecutionId());
        useCase.execute(List.of(), SampleDataFactory.anExecutionId());

        verify(repository, never()).saveAll(anyList(), any());
    }
}
