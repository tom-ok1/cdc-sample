package com.example.consumer.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class DebeziumEnvelope {
    private JsonNode before;
    private JsonNode after;
    private String op;
    private Long ts_ms;
}
