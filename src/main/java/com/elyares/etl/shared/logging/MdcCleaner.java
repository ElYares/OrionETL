package com.elyares.etl.shared.logging;

import org.slf4j.MDC;

/**
 * Utilidad para limpiar entradas del MDC (Mapped Diagnostic Context) de SLF4J.
 *
 * <p>Proporciona métodos estáticos para eliminar claves MDC de forma selectiva
 * o completa. Se utiliza principalmente en filtros de servlet, interceptores y
 * finalizadores de tareas asíncronas para garantizar que los hilos reutilizados
 * de un pool no arrastren contexto de diagnóstico de ejecuciones anteriores.</p>
 *
 * <p>Esta clase no puede ser instanciada ni extendida.</p>
 */
public final class MdcCleaner {

    private MdcCleaner() {}

    /**
     * Elimina todas las entradas del MDC del hilo actual.
     *
     * <p>Equivale a invocar {@link MDC#clear()}. Debe llamarse al final de cada
     * solicitud o tarea para evitar fugas de contexto entre ejecuciones.</p>
     */
    public static void clearAll() {
        MDC.clear();
    }

    /**
     * Elimina del MDC del hilo actual únicamente las claves especificadas.
     *
     * <p>Las claves que no existan en el MDC se ignoran silenciosamente.</p>
     *
     * @param keys claves MDC que deben ser eliminadas
     */
    public static void clear(String... keys) {
        for (String key : keys) {
            MDC.remove(key);
        }
    }
}
