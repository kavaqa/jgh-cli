package dev.mygh.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper for issuing GraphQL queries/mutations and surfacing errors[].
 * The actual HTTP send is delegated back to {@link GitHubClient}.
 */
public final class GraphQL {

    private GraphQL() {
    }

    /** Build the request body {"query":..., "variables":...}. */
    public static String body(String query, Map<String, Object> variables) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("variables", variables != null ? variables : Map.of());
        return Json.write(payload);
    }

    /**
     * Parse a GraphQL response, throwing on a non-empty errors[] array,
     * otherwise returning the "data" node.
     */
    public static JsonNode dataOrThrow(String responseBody) {
        JsonNode root = Json.parse(responseBody);
        JsonNode errors = root.get("errors");
        if (errors != null && errors.isArray() && errors.size() > 0) {
            StringBuilder sb = new StringBuilder("GraphQL error:");
            for (JsonNode err : errors) {
                JsonNode message = err.get("message");
                sb.append("\n  - ").append(message != null ? message.asText() : err.toString());
                JsonNode type = err.get("type");
                if (type != null) {
                    sb.append(" (").append(type.asText()).append(")");
                }
            }
            throw ApiException.api(sb.toString());
        }
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            throw ApiException.api("GraphQL response contained no data");
        }
        return data;
    }
}
