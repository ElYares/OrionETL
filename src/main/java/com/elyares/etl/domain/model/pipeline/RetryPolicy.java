package com.elyares.etl.domain.model.pipeline;

import com.elyares.etl.domain.enums.ErrorType;

import java.util.List;
import java.util.Objects;

/**
 * Value object que define la política de reintentos de un {@link Pipeline}.
 *
 * <p>Encapsula el número máximo de intentos, el retardo entre ellos y los tipos
 * de error sobre los que se permite reintentar. Si la lista de tipos de error
 * está vacía, el reintento se aplica sobre cualquier tipo de error.</p>
 *
 * <p>Las instancias son inmutables. La lista de tipos de error se copia de forma
 * defensiva en el constructor para garantizar su inmutabilidad.</p>
 *
 * <p>Se ofrecen dos métodos de fábrica estáticos para los casos de uso más comunes:
 * {@link #noRetry()} para no reintentar y {@link #of(int, long)} para configurar
 * reintentos con los tipos de error técnicos y de integración externa por defecto.</p>
 */
public final class RetryPolicy {

    private final int maxRetries;
    private final long retryDelayMs;
    private final List<ErrorType> retryOnErrorTypes;

    /**
     * Construye una {@code RetryPolicy} con la configuración proporcionada.
     *
     * @param maxRetries         número máximo de reintentos permitidos; debe ser &ge; 0
     * @param retryDelayMs       tiempo de espera en milisegundos entre reintentos; debe ser &ge; 0
     * @param retryOnErrorTypes  lista de tipos de error sobre los que se aplica el reintento;
     *                           si es {@code null} o vacía se reintenta para cualquier tipo de error
     * @throws IllegalArgumentException si {@code maxRetries} o {@code retryDelayMs} son negativos
     */
    public RetryPolicy(int maxRetries, long retryDelayMs, List<ErrorType> retryOnErrorTypes) {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries cannot be negative");
        if (retryDelayMs < 0) throw new IllegalArgumentException("retryDelayMs cannot be negative");
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.retryOnErrorTypes = retryOnErrorTypes != null ? List.copyOf(retryOnErrorTypes) : List.of();
    }

    /**
     * Crea una política que no permite ningún reintento.
     *
     * @return instancia de {@code RetryPolicy} con {@code maxRetries = 0}
     *         y sin tipos de error configurados
     */
    public static RetryPolicy noRetry() {
        return new RetryPolicy(0, 0, List.of());
    }

    /**
     * Crea una política de reintento con los tipos de error
     * {@link ErrorType#TECHNICAL} y {@link ErrorType#EXTERNAL_INTEGRATION} habilitados por defecto.
     *
     * @param maxRetries   número máximo de reintentos; debe ser &ge; 0
     * @param retryDelayMs tiempo de espera en milisegundos entre reintentos; debe ser &ge; 0
     * @return nueva instancia de {@code RetryPolicy} configurada con los parámetros indicados
     */
    public static RetryPolicy of(int maxRetries, long retryDelayMs) {
        return new RetryPolicy(maxRetries, retryDelayMs, List.of(ErrorType.TECHNICAL, ErrorType.EXTERNAL_INTEGRATION));
    }

    /**
     * Determina si se puede realizar un reintento dado el número de intentos
     * ya realizados y el tipo de error ocurrido.
     *
     * <p>El reintento es posible si {@code currentRetryCount} es menor que
     * {@code maxRetries} y el tipo de error está incluido en la lista configurada
     * (o dicha lista está vacía, lo que permite reintentar cualquier tipo).</p>
     *
     * @param currentRetryCount número de reintentos ya ejecutados
     * @param errorType         tipo del error que originó la evaluación
     * @return {@code true} si se permite un nuevo intento; {@code false} en caso contrario
     */
    public boolean canRetry(int currentRetryCount, ErrorType errorType) {
        if (currentRetryCount >= maxRetries) return false;
        return retryOnErrorTypes.isEmpty() || retryOnErrorTypes.contains(errorType);
    }

    /**
     * Devuelve el número máximo de reintentos configurado.
     *
     * @return número máximo de reintentos; siempre &ge; 0
     */
    public int getMaxRetries() { return maxRetries; }

    /**
     * Devuelve el retardo entre reintentos en milisegundos.
     *
     * @return retardo en milisegundos; siempre &ge; 0
     */
    public long getRetryDelayMs() { return retryDelayMs; }

    /**
     * Devuelve la lista inmutable de tipos de error sobre los que aplica el reintento.
     *
     * <p>Si la lista está vacía, el reintento se aplica para cualquier tipo de error.</p>
     *
     * @return lista inmutable de {@link ErrorType}; nunca {@code null}
     */
    public List<ErrorType> getRetryOnErrorTypes() { return retryOnErrorTypes; }

    /**
     * Compara esta política con otro objeto por valor,
     * considerando {@code maxRetries}, {@code retryDelayMs} y {@code retryOnErrorTypes}.
     *
     * @param o objeto a comparar
     * @return {@code true} si todos los campos son iguales
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RetryPolicy r)) return false;
        return maxRetries == r.maxRetries && retryDelayMs == r.retryDelayMs
            && Objects.equals(retryOnErrorTypes, r.retryOnErrorTypes);
    }

    /**
     * Calcula el código hash a partir de {@code maxRetries}, {@code retryDelayMs}
     * y {@code retryOnErrorTypes}.
     *
     * @return código hash de la política
     */
    @Override
    public int hashCode() {
        return Objects.hash(maxRetries, retryDelayMs, retryOnErrorTypes);
    }
}
