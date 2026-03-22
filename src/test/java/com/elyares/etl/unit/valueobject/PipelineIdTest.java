package com.elyares.etl.unit.valueobject;

import com.elyares.etl.domain.valueobject.PipelineId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias para el value object {@link PipelineId}.
 *
 * <p>Cubre la construcción a partir de un {@link UUID} y de una cadena,
 * la generación de identificadores únicos, el rechazo de valores nulos
 * y la correcta implementación de igualdad y código hash.</p>
 */
class PipelineIdTest {

    /**
     * Verifica que un {@link PipelineId} creado a partir de un {@link UUID}
     * expone el mismo UUID a través de {@link PipelineId#value()}.
     */
    @Test
    void shouldCreateFromUUID() {
        UUID uuid = UUID.randomUUID();
        PipelineId id = PipelineId.of(uuid);
        assertThat(id.value()).isEqualTo(uuid);
    }

    /**
     * Verifica que un {@link PipelineId} creado a partir de una cadena UUID
     * devuelve la misma cadena en {@link PipelineId#toString()}.
     */
    @Test
    void shouldCreateFromString() {
        String uuidStr = UUID.randomUUID().toString();
        PipelineId id = PipelineId.of(uuidStr);
        assertThat(id.toString()).isEqualTo(uuidStr);
    }

    /**
     * Verifica que dos llamadas consecutivas a {@link PipelineId#generate()}
     * producen identificadores distintos.
     */
    @Test
    void shouldGenerateUnique() {
        PipelineId id1 = PipelineId.generate();
        PipelineId id2 = PipelineId.generate();
        assertThat(id1).isNotEqualTo(id2);
    }

    /**
     * Verifica que {@link PipelineId#of(UUID)} lanza
     * {@link NullPointerException} cuando se le pasa un UUID nulo.
     */
    @Test
    void shouldRejectNull() {
        assertThatNullPointerException().isThrownBy(() -> PipelineId.of((UUID) null));
    }

    /**
     * Verifica que dos instancias de {@link PipelineId} construidas a partir
     * del mismo UUID son iguales según {@code equals} y tienen el mismo
     * {@code hashCode}.
     */
    @Test
    void shouldImplementEquality() {
        UUID uuid = UUID.randomUUID();
        PipelineId id1 = PipelineId.of(uuid);
        PipelineId id2 = PipelineId.of(uuid);
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
