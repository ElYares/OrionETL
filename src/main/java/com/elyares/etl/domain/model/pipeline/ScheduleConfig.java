package com.elyares.etl.domain.model.pipeline;

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
        this.cronExpression = cronExpression;
        this.timezone = timezone != null ? timezone : "UTC";
        this.enabled = enabled;
    }

    /**
     * Crea una configuración de schedule deshabilitada.
     *
     * <p>La expresión cron se establece a {@code null} y la zona horaria a {@code "UTC"}.</p>
     *
     * @return instancia de {@code ScheduleConfig} con planificación deshabilitada
     */
    public static ScheduleConfig disabled() {
        return new ScheduleConfig(null, "UTC", false);
    }

    /**
     * Crea una configuración de schedule habilitada con zona horaria {@code "UTC"}.
     *
     * @param cronExpression expresión cron que define la periodicidad de ejecución;
     *                       no debería ser {@code null}
     * @return instancia de {@code ScheduleConfig} habilitada con la expresión proporcionada
     */
    public static ScheduleConfig of(String cronExpression) {
        return new ScheduleConfig(cronExpression, "UTC", true);
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
        return new ScheduleConfig(cronExpression, timezone, true);
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
            && Objects.equals(timezone, s.timezone);
    }

    /**
     * Calcula el código hash a partir de {@code cronExpression}, {@code timezone}
     * y {@code enabled}.
     *
     * @return código hash de la configuración
     */
    @Override
    public int hashCode() {
        return Objects.hash(cronExpression, timezone, enabled);
    }
}
