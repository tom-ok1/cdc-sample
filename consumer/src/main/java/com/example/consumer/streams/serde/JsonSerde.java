package com.example.consumer.streams.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.util.Map;

public class JsonSerde<T> implements Serde<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> targetType;

    public JsonSerde(Class<T> targetType, ObjectMapper objectMapper) {
        this.targetType = targetType;
        this.objectMapper = objectMapper;
    }

    @Override
    public Serializer<T> serializer() {
        return new JsonSerializer<>(objectMapper);
    }

    @Override
    public Deserializer<T> deserializer() {
        return new JsonDeserializer<>(targetType, objectMapper);
    }

    private static class JsonSerializer<T> implements Serializer<T> {
        private final ObjectMapper objectMapper;

        public JsonSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            // No-op
        }

        @Override
        public byte[] serialize(String topic, T data) {
            if (data == null) {
                return null;
            }

            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (IOException e) {
                throw new SerializationException("Error serializing JSON message", e);
            }
        }

        @Override
        public void close() {
            // No-op
        }
    }

    private static class JsonDeserializer<T> implements Deserializer<T> {
        private final Class<T> targetType;
        private final ObjectMapper objectMapper;

        public JsonDeserializer(Class<T> targetType, ObjectMapper objectMapper) {
            this.targetType = targetType;
            this.objectMapper = objectMapper;
        }

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            // No-op
        }

        @Override
        public T deserialize(String topic, byte[] data) {
            if (data == null) {
                return null;
            }

            try {
                return objectMapper.readValue(data, targetType);
            } catch (IOException e) {
                throw new SerializationException("Error deserializing JSON message", e);
            }
        }

        @Override
        public void close() {
            // No-op
        }
    }
}
