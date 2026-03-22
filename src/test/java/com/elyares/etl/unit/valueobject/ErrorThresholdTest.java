package com.elyares.etl.unit.valueobject;

import com.elyares.etl.domain.valueobject.ErrorThreshold;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias para el value object {@link ErrorThreshold}.
 *
 * <p>Verifica las reglas de validación del umbral (rango [0, 100]), la
 * detección correcta de superación del umbral según el ratio de errores
 * calculado y el comportamiento especial ante un total de cero registros.</p>
 */
class ErrorThresholdTest {

    /**
     * Verifica que un umbral con valor porcentual dentro del rango válido
     * se crea correctamente y expone el valor esperado.
     */
    @Test
    void shouldCreateValidThreshold() {
        ErrorThreshold threshold = ErrorThreshold.of(5.0);
        assertThat(threshold.percentValue()).isEqualTo(5.0);
    }

    /**
     * Verifica que {@link ErrorThreshold#of(double)} lanza
     * {@link IllegalArgumentException} cuando el valor es negativo.
     */
    @Test
    void shouldRejectNegativeThreshold() {
        assertThatIllegalArgumentException().isThrownBy(() -> ErrorThreshold.of(-1.0));
    }

    /**
     * Verifica que {@link ErrorThreshold#of(double)} lanza
     * {@link IllegalArgumentException} cuando el valor supera 100.
     */
    @Test
    void shouldRejectThresholdOver100() {
        assertThatIllegalArgumentException().isThrownBy(() -> ErrorThreshold.of(101.0));
    }

    /**
     * Verifica que {@link ErrorThreshold#isBreached(long, long)} devuelve
     * {@code true} cuando el porcentaje de registros con error supera el umbral.
     */
    @Test
    void shouldDetectBreachedThreshold() {
        ErrorThreshold threshold = ErrorThreshold.of(10.0);
        assertThat(threshold.isBreached(100, 15)).isTrue();
    }

    /**
     * Verifica que {@link ErrorThreshold#isBreached(long, long)} devuelve
     * {@code false} cuando el porcentaje de registros con error no supera el umbral.
     */
    @Test
    void shouldDetectNotBreachedThreshold() {
        ErrorThreshold threshold = ErrorThreshold.of(10.0);
        assertThat(threshold.isBreached(100, 5)).isFalse();
    }

    /**
     * Verifica que {@link ErrorThreshold#isBreached(long, long)} devuelve
     * {@code false} cuando el total de registros procesados es cero, evitando
     * una división por cero.
     */
    @Test
    void shouldHandleZeroTotalRecords() {
        ErrorThreshold threshold = ErrorThreshold.of(5.0);
        assertThat(threshold.isBreached(0, 0)).isFalse();
    }
}
