package dev.mygh.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

/**
 * Thin wrapper over Jackson so the rest of the code does not depend on it directly.
 * If the JSON library ever needs to change, only this class is affected.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper PRETTY =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private Json() {
    }

    /** Serialize an object (typically a Map) to a compact JSON string. */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("failed to serialize JSON", e);
        }
    }

    /** Serialize an object to a pretty-printed JSON string (for --json output). */
    public static String writePretty(Object value) {
        try {
            return PRETTY.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("failed to serialize JSON", e);
        }
    }

    /** Parse a JSON string into a navigable tree. */
    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("failed to parse JSON response: " + e.getMessage(), e);
        }
    }

    /** Pretty-print an already-parsed JSON node. */
    public static String pretty(JsonNode node) {
        try {
            return PRETTY.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("failed to serialize JSON", e);
        }
    }

    /** Build a JSON object body from key/value pairs, skipping null values. */
    public static String object(Map<String, ?> fields) {
        return write(fields);
    }
}
