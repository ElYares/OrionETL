package com.elyares.etl.domain.model.pipeline;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Value object que define la configuración de planificación horaria de un {@link Pipeline}.
 *
 * <p>Encapsula una expresión cron que determina la frecuencia de ejecución,
 * la zona horaria en la que debe interpretarse dicha expresión y un indicador
 * de si la planificación está habilitada.</p>
 *
 * <p>Cuando {@code enabled} es {@code false} el pipeline no se ejecutará de forma
 * automática independientemente del valor de {@code cronExpression}.</p>
 *
 * <p>Las instancias son inmutables. Se proporcionan tres métodos de fábrica estáticos
 * para construir los casos de uso más habituales.</p>
 */
public final class ScheduleConfig {

    private final String cronExpression;
    private final String timezone;
    private final boolean enabled;
    private final List<AllowedWindow> allowedWindows;

    /**
     * Construye una {@code ScheduleConfig} con los parámetros indicados.
     *
     * <p>Si {@code timezone} es {@code null}, se establece {@code "UTC"} como valor por defecto.</p>
     *
     * @param cronExpression expresión cron que define la periodicidad de ejecución;
     *                       puede ser {@code null} cuando la planificación está deshabilitada
     * @param timezone       identificador de zona horaria (p. ej. {@code "Europe/Madrid"});
     *                       si es {@code null} se usa {@code "UTC"}
     * @param enabled        {@code true} para habilitar la ejecución programada
     */
    public ScheduleConfig(String cronExpression, String timezone, boolean enabled) {
        this(cronExpression, timezone, enabled, List.of());
    }

    /**
     * Construye una {@code ScheduleConfig} con ventanas horarias opcionales.
     *
     * @param cronExpression expresión cron que define la periodicidad de ejecución
     * @param timezone       identificador de zona horaria
     * @param enabled        indicador de schedule habilitado
     * @param allowedWindows ventanas permitidas de ejecución (vacío = sin restricción)
     */
    public ScheduleConfig(String cronExpression, String timezone, boolean enabled,
                          List<AllowedWindow> allowedWindows) {
        this.cronExpression = cronExpression;
        this.timezone = timezone != null ? timezone : "UTC";
        this.enabled = enabled;
        this.allowedWindows = allowedWindows != null ? List.copyOf(allowedWindows) : List.of();
    }

    /**
     * Crea una configuración de schedule deshabilitada.
     *
     * <p>La expresión cron se establece a {@code null} y la zona horaria a {@code "UTC"}.</p>
     *
     * @return instancia de {@code ScheduleConfig} con planificación deshabilitada
     */
    public static ScheduleConfig disabled() {
        return new ScheduleConfig(null, "UTC", false, List.of());
    }

    /**
     * Crea una configuración de schedule habilitada con zona horaria {@code "UTC"}.
     *
     * @param cronExpression expresión cron que define la periodicidad de ejecución;
     *                       no debería ser {@code null}
     * @return instancia de {@code ScheduleConfig} habilitada con la expresión proporcionada
     */
    public static ScheduleConfig of(String cronExpression) {
        return new ScheduleConfig(cronExpression, "UTC", true, List.of());
    }

    /**
     * Crea una configuración de schedule habilitada con zona horaria explícita.
     *
     * @param cronExpression expresión cron que define la periodicidad de ejecución;
     *                       no debería ser {@code null}
     * @param timezone       identificador de zona horaria (p. ej. {@code "America/New_York"});
     *                       si es {@code null} se usa {@code "UTC"}
     * @return instancia de {@code ScheduleConfig} habilitada con los parámetros proporcionados
     */
    public static ScheduleConfig of(String cronExpression, String timezone) {
        return new ScheduleConfig(cronExpression, timezone, true, List.of());
    }

    /**
     * Crea una configuración de schedule habilitada con ventanas horarias permitidas.
     *
     * @param cronExpression expresión cron
     * @param timezone       zona horaria
     * @param allowedWindows ventanas permitidas de ejecución
     * @return configuración de schedule con restricciones de ventana
     */
    public static ScheduleConfig of(String cronExpression, String timezone,
                                    List<AllowedWindow> allowedWindows) {
        return new ScheduleConfig(cronExpression, timezone, true, allowedWindows);
    }

    /**
     * Devuelve la expresión cron configurada.
     *
     * @return expresión cron, o {@code null} si la planificación está deshabilitada
     */
    public String getCronExpression() { return cronExpression; }

    /**
     * Devuelve la zona horaria en la que debe interpretarse la expresión cron.
     *
     * @return identificador de zona horaria; nunca {@code null}
     */
    public String getTimezone() { return timezone; }

    /**
     * Indica si la ejecución programada está habilitada.
     *
     * @return {@code true} si el schedule está activo; {@code false} en caso contrario
     */
    public boolean isEnabled() { return enabled; }

    /**
     * Devuelve la lista de ventanas permitidas de ejecución.
     *
     * @return lista inmutable de ventanas; vacía si no hay restricciones horarias
     */
    public List<AllowedWindow> getAllowedWindows() { return allowedWindows; }

    /**
     * Indica si una fecha/hora cae dentro de al menos una ventana permitida.
     *
     * @param now fecha/hora zonificada a evaluar
     * @return {@code true} si no hay ventanas configuradas o si alguna coincide
     */
    public boolean isWithinAllowedWindow(ZonedDateTime now) {
        if (allowedWindows.isEmpty()) {
            return true;
        }
        return allowedWindows.stream().anyMatch(window -> window.matches(now));
    }

    /**
     * Compara esta configuración con otro objeto por valor,
     * considerando {@code cronExpression}, {@code timezone} y {@code enabled}.
     *
     * @param o objeto a comparar
     * @return {@code true} si todos los campos son iguales
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScheduleConfig s)) return false;
        return enabled == s.enabled
            && Objects.equals(cronExpression, s.cronExpression)
            && Objects.equals(timezone, s.timezone)
            && Objects.equals(allowedWindows, s.allowedWindows);
    }

    /**
     * Calcula el código hash a partir de {@code cronExpression}, {@code timezone}
     * y {@code enabled}.
     *
     * @return código hash de la configuración
     */
    @Override
    public int hashCode() {
        return Objects.hash(cronExpression, timezone, enabled, allowedWindows);
    }

    /**
     * Ventana horaria de ejecución permitida.
     *
     * @param start hora de inicio (incluyente)
     * @param end   hora de fin (incluyente)
     * @param days  días de semana permitidos
     */
    public record AllowedWindow(LocalTime start, LocalTime end, List<DayOfWeek> days) {
        public AllowedWindow {
            Objects.requireNonNull(start, "start must not be null");
            Objects.requireNonNull(end, "end must not be null");
            days = days != null ? List.copyOf(days) : List.of();
        }

        public boolean matches(ZonedDateTime now) {
            boolean dayAllowed = days.isEmpty() || days.contains(now.getDayOfWeek());
            if (!dayAllowed) {
                return false;
            }
            LocalTime time = now.toLocalTime();
            return !time.isBefore(start) && !time.isAfter(end);
        }
    }
}
