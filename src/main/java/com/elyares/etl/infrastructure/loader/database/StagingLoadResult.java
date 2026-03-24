package com.elyares.etl.infrastructure.loader.database;

/**
 * Resultado interno de la carga a staging por chunks.
 */
public record StagingLoadResult(long stagedCount, long rejectedCount, boolean successful, String errorDetail) {

    static StagingLoadResult success(long stagedCount, long rejectedCount) {
        return new StagingLoadResult(stagedCount, rejectedCount, true, null);
    }

    static StagingLoadResult failure(long stagedCount, long rejectedCount, String errorDetail) {
        return new StagingLoadResult(stagedCount, rejectedCount, false, errorDetail);
    }
}
