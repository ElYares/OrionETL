package com.elyares.etl.unit.util;

import com.elyares.etl.shared.util.StringUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias para {@link StringUtils}.
 *
 * <p>Cubre las operaciones de recorte de espacios, conversión de nomenclatura
 * (camelCase a snake_case, Title Case), validación y normalización de
 * direcciones de correo electrónico.</p>
 */
class StringUtilsTest {

    /**
     * Verifica que {@link StringUtils#trimToNull(String)} devuelve {@code null}
     * tanto para cadenas que contienen únicamente espacios como para valores nulos.
     */
    @Test
    void trimToNullShouldReturnNullForBlank() {
        assertThat(StringUtils.trimToNull("   ")).isNull();
        assertThat(StringUtils.trimToNull(null)).isNull();
    }

    /**
     * Verifica que {@link StringUtils#trimToNull(String)} devuelve la cadena
     * recortada cuando contiene caracteres no espaciados.
     */
    @Test
    void trimToNullShouldReturnTrimmedValue() {
        assertThat(StringUtils.trimToNull("  hello  ")).isEqualTo("hello");
    }

    /**
     * Verifica que {@link StringUtils#toSnakeCase(String)} convierte correctamente
     * identificadores camelCase a snake_case en minúsculas.
     */
    @Test
    void toSnakeCaseShouldConvertCamelCase() {
        assertThat(StringUtils.toSnakeCase("transactionId")).isEqualTo("transaction_id");
        assertThat(StringUtils.toSnakeCase("saleDate")).isEqualTo("sale_date");
    }

    /**
     * Verifica que {@link StringUtils#toTitleCase(String)} capitaliza la primera
     * letra de cada palabra en una cadena de múltiples palabras.
     */
    @Test
    void toTitleCaseShouldCapitalizeWords() {
        assertThat(StringUtils.toTitleCase("john doe")).isEqualTo("John Doe");
    }

    /**
     * Verifica que {@link StringUtils#isValidEmail(String)} acepta direcciones
     * correctas y rechaza cadenas mal formadas y valores nulos.
     */
    @Test
    void isValidEmailShouldValidateCorrectly() {
        assertThat(StringUtils.isValidEmail("user@example.com")).isTrue();
        assertThat(StringUtils.isValidEmail("not-an-email")).isFalse();
        assertThat(StringUtils.isValidEmail(null)).isFalse();
    }

    /**
     * Verifica que {@link StringUtils#normalizeEmail(String)} convierte la
     * dirección a minúsculas eliminando diferencias de capitalización.
     */
    @Test
    void normalizeEmailShouldLowercase() {
        assertThat(StringUtils.normalizeEmail("USER@EXAMPLE.COM")).isEqualTo("user@example.com");
    }
}
