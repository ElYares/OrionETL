package com.elyares.etl.domain.valueobject;

import java.util.Objects;

/**
 * Objeto de valor que representa una clave de negocio compuesta por un campo y su valor.
 *
 * <p>Una clave de negocio identifica de forma unívoca un registro dentro del dominio de negocio,
 * a diferencia de los identificadores técnicos como los UUIDs. Se utiliza principalmente en
 * la estrategia de carga {@code UPSERT} para determinar si un registro ya existe en el destino
 * y debe ser actualizado o si debe ser insertado como nuevo.</p>
 *
 * <p>Ejemplo de uso: {@code BusinessKey.of("email", "usuario@ejemplo.com")} identifica
 * a un registro cuyo campo {@code email} toma el valor {@code usuario@ejemplo.com}.</p>
 *
 * <p>Al ser un {@code record} de Java, esta clase es inmutable y la igualdad estructural
 * está basada en los valores de {@code field} y {@code value}.</p>
 *
 * @param field nombre del campo que actúa como clave de negocio (p. ej. {@code "email"}, {@code "nif"}).
 * @param value valor del campo para el registro concreto (p. ej. {@code "usuario@ejemplo.com"}).
 */
public record BusinessKey(String field, String value) {

    /**
     * Constructor compacto que valida que ni el campo ni el valor sean nulos.
     *
     * @param field nombre del campo clave de negocio; no puede ser {@code null}.
     * @param value valor del campo; no puede ser {@code null}.
     * @throws NullPointerException si {@code field} o {@code value} son {@code null}.
     */
    public BusinessKey {
        Objects.requireNonNull(field, "BusinessKey field must not be null");
        Objects.requireNonNull(value, "BusinessKey value must not be null");
    }

    /**
     * Crea un {@code BusinessKey} a partir del nombre del campo y su valor.
     *
     * @param field nombre del campo que actúa como clave de negocio.
     * @param value valor del campo para el registro concreto.
     * @return nueva instancia de {@code BusinessKey}.
     * @throws NullPointerException si alguno de los argumentos es {@code null}.
     */
    public static BusinessKey of(String field, String value) {
        return new BusinessKey(field, value);
    }

    /**
     * Devuelve la representación en cadena de la clave de negocio en formato {@code campo=valor}.
     *
     * @return cadena con el formato {@code "<field>=<value>"} (p. ej. {@code "email=usuario@ejemplo.com"}).
     */
    @Override
    public String toString() {
        return field + "=" + value;
    }
}
