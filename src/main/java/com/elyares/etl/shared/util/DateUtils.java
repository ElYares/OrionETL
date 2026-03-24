package com.elyares.etl.shared.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Utilidades para el análisis, conversión y validación de fechas y marcas temporales.
 *
 * <p>Soporta múltiples formatos de fecha y fecha-hora de uso común en fuentes de datos
 * ETL. Los métodos de análisis iteran sobre la lista de formateadores soportados y
 * devuelven el primer resultado exitoso, lanzando {@link IllegalArgumentException}
 * si ningún formato es aplicable.</p>
 *
 * <p>Esta clase no puede ser instanciada ni extendida.</p>
 */
public final class DateUtils {

    /**
     * Lista de formateadores soportados para cadenas de fecha ({@link LocalDate}).
     * Se prueban en orden; el primero que tenga éxito determina el resultado.
     */
    private static final List<DateTimeFormatter> SUPPORTED_DATE_FORMATTERS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    /**
     * Lista de formateadores soportados para cadenas de fecha-hora ({@link LocalDateTime}).
     * Se prueban en orden; el primero que tenga éxito determina el resultado.
     */
    private static final List<DateTimeFormatter> SUPPORTED_DATETIME_FORMATTERS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")
    );

    private DateUtils() {}

    /**
     * Convierte una cadena de fecha-hora en un {@link Instant} normalizado a UTC.
     *
     * <p>Si la entrada no incluye componente de hora pero sí representa una fecha válida,
     * se normaliza al inicio del día en la zona de origen. Esto permite reutilizar el
     * mismo helper para campos DATE y DATETIME dentro del pipeline.</p>
     *
     * @param dateTimeStr cadena de fecha-hora a convertir
     * @param sourceZone  zona horaria en la que está expresado {@code dateTimeStr}
     * @return instante equivalente en UTC
     * @throws IllegalArgumentException si ningún formato soportado puede analizar la cadena
     */
    public static Instant toUtcInstant(String dateTimeStr, ZoneId sourceZone) {
        for (DateTimeFormatter fmt : SUPPORTED_DATETIME_FORMATTERS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(dateTimeStr, fmt);
                return ldt.atZone(sourceZone).toInstant();
            } catch (DateTimeParseException ignored) {}
        }
        try {
            return parseDate(dateTimeStr).atStartOfDay(sourceZone).toInstant();
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Cannot parse datetime: " + dateTimeStr);
        }
    }

    /**
     * Analiza una cadena de fecha usando los formatos soportados por
     * {@link #SUPPORTED_DATE_FORMATTERS}.
     *
     * @param dateStr cadena de fecha a analizar
     * @return instancia de {@link LocalDate} correspondiente
     * @throws IllegalArgumentException si ningún formato soportado puede analizar la cadena
     */
    public static LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter fmt : SUPPORTED_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Cannot parse date: " + dateStr);
    }

    /**
     * Comprueba si una cadena representa una fecha válida según los formatos soportados.
     *
     * @param dateStr cadena a evaluar; puede ser {@code null} o vacía
     * @return {@code true} si la cadena puede ser analizada como fecha válida;
     *         {@code false} en caso contrario
     */
    public static boolean isValidDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return false;
        try {
            parseDate(dateStr);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Determina si una fecha es posterior a la fecha actual en UTC.
     *
     * @param date fecha a evaluar
     * @return {@code true} si {@code date} es estrictamente posterior al día actual en UTC
     */
    public static boolean isFutureDate(LocalDate date) {
        return date.isAfter(LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Devuelve el instante actual en UTC.
     *
     * @return instante actual según el reloj del sistema
     */
    public static Instant nowUtc() {
        return Instant.now();
    }
}
