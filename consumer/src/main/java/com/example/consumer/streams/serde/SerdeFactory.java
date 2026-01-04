package com.example.consumer.streams.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;

public class SerdeFactory {

    public static <T> Serde<T> createJsonSerde(Class<T> type, ObjectMapper mapper) {
        return new JsonSerde<>(type, mapper);
    }
}
