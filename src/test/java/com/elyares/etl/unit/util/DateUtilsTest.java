package com.elyares.etl.unit.util;

import com.elyares.etl.shared.util.DateUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias para {@link DateUtils}.
 *
 * <p>Cubre el análisis de fechas en múltiples formatos, la detección de fechas
 * inválidas, la clasificación de fechas futuras y el comportamiento ante
 * entradas no analizables.</p>
 */
class DateUtilsTest {

    /**
     * Verifica que una fecha en formato ISO ({@code yyyy-MM-dd}) se analiza
     * correctamente a sus componentes de año, mes y día.
     */
    @Test
    void shouldParseIsoDate() {
        LocalDate date = DateUtils.parseDate("2026-01-15");
        assertThat(date.getYear()).isEqualTo(2026);
        assertThat(date.getMonthValue()).isEqualTo(1);
        assertThat(date.getDayOfMonth()).isEqualTo(15);
    }

    /**
     * Verifica que una fecha en formato {@code dd/MM/yyyy} se analiza
     * correctamente y devuelve el año esperado.
     */
    @Test
    void shouldParseSlashDate() {
        LocalDate date = DateUtils.parseDate("15/01/2026");
        assertThat(date.getYear()).isEqualTo(2026);
    }

    /**
     * Verifica que {@link DateUtils#isValidDate(String)} devuelve {@code false}
     * para cadenas que no representan fechas válidas y para valores nulos.
     */
    @Test
    void shouldDetectInvalidDate() {
        assertThat(DateUtils.isValidDate("not-a-date")).isFalse();
        assertThat(DateUtils.isValidDate(null)).isFalse();
    }

    /**
     * Verifica que una fecha diez días en el futuro es correctamente detectada
     * como fecha futura.
     */
    @Test
    void shouldDetectFutureDate() {
        LocalDate future = LocalDate.now().plusDays(10);
        assertThat(DateUtils.isFutureDate(future)).isTrue();
    }

    /**
     * Verifica que una fecha del día anterior no es considerada futura.
     */
    @Test
    void shouldDetectNonFutureDate() {
        LocalDate past = LocalDate.now().minusDays(1);
        assertThat(DateUtils.isFutureDate(past)).isFalse();
    }

    /**
     * Verifica que {@link DateUtils#parseDate(String)} lanza
     * {@link IllegalArgumentException} cuando la cadena no puede ser analizada
     * por ninguno de los formatos soportados.
     */
    @Test
    void shouldThrowForUnparseable() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DateUtils.parseDate("garbage"));
    }
}
