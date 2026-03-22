package com.elyares.etl.unit.valueobject;

import com.elyares.etl.domain.valueobject.RecordCount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias para el value object {@link RecordCount}.
 *
 * <p>Cubre la creación del contador en cero, la operación de suma, el rechazo
 * de valores negativos y la detección del estado cero mediante
 * {@link RecordCount#isZero()}.</p>
 */
class RecordCountTest {

    /**
     * Verifica que {@link RecordCount#zero()} crea un contador con valor cero.
     */
    @Test
    void shouldCreateZero() {
        assertThat(RecordCount.zero().value()).isZero();
    }

    /**
     * Verifica que {@link RecordCount#add(long)} devuelve un nuevo contador
     * cuyo valor es la suma del original más el incremento indicado.
     */
    @Test
    void shouldAdd() {
        RecordCount count = RecordCount.of(10).add(5);
        assertThat(count.value()).isEqualTo(15);
    }

    /**
     * Verifica que {@link RecordCount#of(long)} lanza
     * {@link IllegalArgumentException} cuando se le pasa un valor negativo.
     */
    @Test
    void shouldRejectNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> RecordCount.of(-1));
    }

    /**
     * Verifica que {@link RecordCount#isZero()} devuelve {@code true} para un
     * contador con valor cero y {@code false} para uno con valor positivo.
     */
    @Test
    void shouldDetectZero() {
        assertThat(RecordCount.zero().isZero()).isTrue();
        assertThat(RecordCount.of(1).isZero()).isFalse();
    }
}
