package com.elyares.etl.shared.util;

/**
 * Utilidades para el tratamiento y validación de cadenas de texto.
 *
 * <p>Complementa la API estándar de {@link String} con operaciones de uso
 * frecuente en procesos ETL: normalización de espacios, conversión de
 * nomenclatura, validación de correo electrónico y truncado de longitud.</p>
 *
 * <p>Esta clase no puede ser instanciada ni extendida.</p>
 */
public final class StringUtils {

    private StringUtils() {}

    /**
     * Elimina los espacios iniciales y finales de una cadena; devuelve
     * {@code null} si la cadena resultante está vacía.
     *
     * @param value cadena a procesar; puede ser {@code null}
     * @return cadena recortada, o {@code null} si el valor original era
     *         {@code null} o contenía únicamente espacios
     */
    public static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Elimina los espacios iniciales y finales de una cadena; devuelve
     * una cadena vacía si el valor original era {@code null}.
     *
     * @param value cadena a procesar; puede ser {@code null}
     * @return cadena recortada, nunca {@code null}
     */
    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Determina si una cadena es {@code null} o contiene únicamente espacios en blanco.
     *
     * @param value cadena a evaluar
     * @return {@code true} si el valor es {@code null} o está en blanco
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Determina si una cadena no es {@code null} y contiene al menos un carácter
     * no espaciado.
     *
     * @param value cadena a evaluar
     * @return {@code true} si el valor no es nulo ni está en blanco
     */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    /**
     * Convierte una cadena en notación camelCase o PascalCase a notación snake_case.
     *
     * <p>Ejemplo: {@code "transactionId"} → {@code "transaction_id"}</p>
     *
     * @param camelCase cadena en notación camelCase; puede ser {@code null}
     * @return cadena equivalente en snake_case en minúsculas, o {@code null} si
     *         el valor de entrada es {@code null}
     */
    public static String toSnakeCase(String camelCase) {
        if (camelCase == null) return null;
        return camelCase
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .toLowerCase();
    }

    /**
     * Convierte una cadena a notación Title Case capitalizando la primera letra
     * de cada palabra y poniendo el resto en minúsculas.
     *
     * <p>Ejemplo: {@code "john doe"} → {@code "John Doe"}</p>
     *
     * @param value cadena a convertir; puede ser {@code null} o en blanco
     * @return cadena en Title Case, o el valor original si era nulo o en blanco
     */
    public static String toTitleCase(String value) {
        if (isBlank(value)) return value;
        String[] words = value.trim().toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1))
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Normaliza una dirección de correo electrónico eliminando espacios y
     * convirtiendo todos los caracteres a minúsculas.
     *
     * @param email dirección de correo a normalizar; puede ser {@code null}
     * @return dirección normalizada, o {@code null} si el valor de entrada es {@code null}
     */
    public static String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    /**
     * Valida si una cadena tiene el formato de una dirección de correo electrónico válida.
     *
     * <p>El patrón exige al menos un carácter local, el símbolo {@code @}, un dominio
     * con al menos un punto y una extensión de dominio de dos o más caracteres.</p>
     *
     * @param email dirección a validar; puede ser {@code null} o en blanco
     * @return {@code true} si la cadena cumple el formato de correo electrónico
     */
    public static boolean isValidEmail(String email) {
        if (isBlank(email)) return false;
        return email.trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    /**
     * Trunca una cadena a la longitud máxima indicada.
     *
     * <p>Si la cadena es {@code null} o su longitud es menor o igual a
     * {@code maxLength}, se devuelve sin modificación.</p>
     *
     * @param value     cadena a truncar; puede ser {@code null}
     * @param maxLength número máximo de caracteres permitidos
     * @return cadena truncada a {@code maxLength} caracteres, o el valor original
     *         si no supera ese límite
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
