package com.elyares.etl.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.elyares.etl.shared.exception.EtlException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PersistenceJsonMapper {

    private final ObjectMapper objectMapper;

    public PersistenceJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new EtlException("ETL_JSON_SERIALIZATION_ERROR", "Unable to serialize JSON payload", ex);
        }
    }

    public <T> T fromJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new EtlException("ETL_JSON_DESERIALIZATION_ERROR", "Unable to deserialize JSON payload", ex);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap(String value) {
        return fromJson(value, Map.class);
    }
}
