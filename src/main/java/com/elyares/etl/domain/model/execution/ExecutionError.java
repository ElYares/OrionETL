package com.elyares.etl.domain.model.execution;

import com.elyares.etl.domain.enums.ErrorSeverity;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.valueobject.ExecutionId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa un error ocurrido durante la ejecución de un pipeline ETL.
 *
 * <p>Registra información completa sobre el error: el paso del pipeline donde se produjo,
 * el tipo y la severidad del error, un código de error, el mensaje descriptivo,
 * la traza de la pila ({@code stackTrace}) y una referencia opcional al registro de datos
 * que desencadenó el error.</p>
 *
 * <p>Si {@code severity} es {@code null} al construir la instancia, se asigna
 * {@link ErrorSeverity#ERROR} como valor por defecto.</p>
 *
 * <p>Las instancias son inmutables: todos los campos se fijan en el constructor.</p>
 */
public final class ExecutionError {

    private final UUID id;
    private final ExecutionId executionId;
    private final String stepName;
    private final ErrorType errorType;
    private final ErrorSeverity severity;
    private final String errorCode;
    private final String message;
    private final String stackTrace;
    private final String recordReference;
    private final Instant createdAt;

    /**
     * Construye un nuevo {@code ExecutionError} con todos sus atributos.
     *
     * <p>Si {@code id} es {@code null}, se genera un {@link UUID} aleatorio.
     * Si {@code severity} es {@code null}, se usa {@link ErrorSeverity#ERROR} por defecto.
     * La marca de tiempo {@code createdAt} se fija con {@link Instant#now()}.</p>
     *
     * @param id              identificador único del error; si es {@code null} se genera automáticamente
     * @param executionId     identificador de la ejecución en la que ocurrió el error;
     *                        no puede ser {@code null}
     * @param stepName        nombre del paso del pipeline donde se produjo el error;
     *                        puede ser {@code null} si el error no está asociado a un paso concreto
     * @param errorType       clasificación del tipo de error; no puede ser {@code null}
     * @param severity        nivel de severidad del error; si es {@code null} se usa {@code ERROR}
     * @param errorCode       código identificador del error (p. ej. {@code "DB_CONN_TIMEOUT"});
     *                        puede ser {@code null}
     * @param message         descripción legible del error; no puede ser {@code null}
     * @param stackTrace      traza completa de la excepción; puede ser {@code null}
     * @param recordReference referencia al registro de datos que provocó el error; puede ser {@code null}
     * @throws NullPointerException si {@code executionId}, {@code errorType} o {@code message}
     *                              son {@code null}
     */
    public ExecutionError(UUID id, ExecutionId executionId, String stepName, ErrorType errorType,
                          ErrorSeverity severity, String errorCode, String message,
                          String stackTrace, String recordReference) {
        this.id = id != null ? id : UUID.randomUUID();
        this.executionId = Objects.requireNonNull(executionId);
        this.stepName = stepName;
        this.errorType = Objects.requireNonNull(errorType);
        this.severity = severity != null ? severity : ErrorSeverity.ERROR;
        this.errorCode = errorCode;
        this.message = Objects.requireNonNull(message);
        this.stackTrace = stackTrace;
        this.recordReference = recordReference;
        this.createdAt = Instant.now();
    }

    /**
     * Devuelve el identificador único de este error.
     *
     * @return UUID del error; nunca {@code null}
     */
    public UUID getId() { return id; }

    /**
     * Devuelve el identificador de la ejecución en la que se produjo este error.
     *
     * @return identificador de ejecución; nunca {@code null}
     */
    public ExecutionId getExecutionId() { return executionId; }

    /**
     * Devuelve el nombre del paso del pipeline donde ocurrió el error.
     *
     * @return nombre del paso, o {@code null} si el error no está asociado a un paso concreto
     */
    public String getStepName() { return stepName; }

    /**
     * Devuelve la clasificación del tipo de error.
     *
     * @return tipo del error; nunca {@code null}
     */
    public ErrorType getErrorType() { return errorType; }

    /**
     * Devuelve el nivel de severidad del error.
     *
     * @return severidad del error; nunca {@code null}
     */
    public ErrorSeverity getSeverity() { return severity; }

    /**
     * Devuelve el código identificador del error.
     *
     * @return código de error, o {@code null} si no fue especificado
     */
    public String getErrorCode() { return errorCode; }

    /**
     * Devuelve el mensaje descriptivo del error.
     *
     * @return mensaje del error; nunca {@code null}
     */
    public String getMessage() { return message; }

    /**
     * Devuelve la traza de pila completa de la excepción que originó el error.
     *
     * @return traza de la pila, o {@code null} si no fue capturada
     */
    public String getStackTrace() { return stackTrace; }

    /**
     * Devuelve la referencia al registro de datos que desencadenó el error.
     *
     * <p>Puede ser, por ejemplo, el identificador de fila o clave primaria del registro
     * que falló la validación o el procesamiento.</p>
     *
     * @return referencia al registro, o {@code null} si el error no está asociado a un registro concreto
     */
    public String getRecordReference() { return recordReference; }

    /**
     * Devuelve la marca de tiempo en que fue registrado este error.
     *
     * @return instante de registro; nunca {@code null}
     */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Indica si este error tiene severidad crítica.
     *
     * @return {@code true} si {@code severity} es {@link ErrorSeverity#CRITICAL};
     *         {@code false} en caso contrario
     */
    public boolean isCritical() { return severity == ErrorSeverity.CRITICAL; }
}
