package com.elyares.etl.domain.enums;

/**
 * Clasifica la naturaleza de un error producido durante la ejecución de un pipeline ETL.
 *
 * <p>La categorización del tipo de error permite aplicar estrategias de manejo diferenciadas,
 * enrutar alertas al equipo responsable y generar informes de calidad precisos.</p>
 */
public enum ErrorType {

    /**
     * Error originado por un fallo en la infraestructura o en el propio sistema ETL,
     * como excepciones de I/O, problemas de conectividad o fallos en tiempo de ejecución.
     */
    TECHNICAL,

    /**
     * Error producido por incumplimiento de una regla de negocio, como valores fuera
     * de rango permitido o secuencias de estado inválidas.
     */
    FUNCTIONAL,

    /**
     * Error relacionado con la calidad del dato de origen, incluyendo campos nulos
     * obligatorios, formatos incorrectos o valores duplicados no permitidos.
     */
    DATA_QUALITY,

    /**
     * Error ocurrido durante la comunicación con un sistema externo, como una API,
     * un servicio web o una base de datos remota.
     */
    EXTERNAL_INTEGRATION
}
