package com.elyares.etl.shared.response;

import java.util.List;

/**
 * Estructura de respuesta paginada para colecciones de recursos en la API REST.
 *
 * <p>Encapsula el contenido de la página actual junto con metadatos de
 * paginación: número de página ({@code page}, base cero), tamaño de página
 * ({@code size}), total de elementos en todas las páginas ({@code totalElements}),
 * número total de páginas ({@code totalPages}) e indicador de última página
 * ({@code last}).</p>
 *
 * <p>Se recomienda construir instancias mediante el método de fábrica
 * {@link #of(List, int, int, long)}, que calcula automáticamente
 * {@code totalPages} y {@code last}.</p>
 *
 * @param <T> tipo de los elementos contenidos en la página
 */
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {
    /**
     * Crea una respuesta paginada calculando automáticamente el número total
     * de páginas y si la página actual es la última.
     *
     * <p>Si {@code size} es cero se devuelve {@code totalPages = 0} para
     * evitar una división por cero.</p>
     *
     * @param <T>           tipo de los elementos del contenido
     * @param content       lista de elementos de la página actual
     * @param page          número de página solicitado (base cero)
     * @param size          número máximo de elementos por página
     * @param totalElements número total de elementos en el conjunto completo
     * @return instancia con {@code totalPages} y {@code last} calculados
     */
    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean last = page >= totalPages - 1;
        return new PagedResponse<>(content, page, size, totalElements, totalPages, last);
    }
}
