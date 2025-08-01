/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.extern.log4j.Log4j2;

/**
 * RemoteResponseMapper handles mapping of remote search engine responses to OpenSearch format
 * using JSON path-based field mapping. This enables experiments to work with responses from
 * different search engines by transforming them into a consistent format.
 */
@Log4j2
public class RemoteResponseMapper {

    /**
     * Map a remote search response to OpenSearch format using response template
     *
     * @param rawResponse The raw JSON response from remote search engine
     * @param responseTemplate The response template defining field mappings (JSON path format)
     * @return Mapped response in OpenSearch format
     */
    public String mapResponse(String rawResponse, String responseTemplate) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return createEmptyResponse();
        }

        if (responseTemplate == null || responseTemplate.trim().isEmpty()) {
            // No template provided, attempt default OpenSearch format detection
            return mapWithDefaultTemplate(rawResponse);
        }

        try {
            // Parse the raw response
            Map<String, Object> rawData = parseJsonToMap(rawResponse);

            // Parse the response template
            Map<String, Object> template = parseJsonToMap(responseTemplate);

            // Apply the mapping
            Map<String, Object> mappedData = applyMapping(rawData, template);

            // Convert back to JSON
            return mapToJson(mappedData);

        } catch (Exception e) {
            log.error("Failed to map remote response: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Apply default mapping for responses that might already be in OpenSearch format
     */
    private String mapWithDefaultTemplate(String rawResponse) {
        try {
            Map<String, Object> rawData = parseJsonToMap(rawResponse);

            // Check if it's already in OpenSearch format
            if (rawData.containsKey("hits")) {
                return rawResponse; // Already in correct format
            }

            // Try to detect common search response patterns
            if (rawData.containsKey("results") || rawData.containsKey("documents")) {
                return mapCommonFormat(rawData);
            }

            // If we can't detect the format, wrap it in a basic structure
            return wrapInBasicFormat(rawData);

        } catch (Exception e) {
            log.warn("Failed to apply default mapping, returning raw response: {}", e.getMessage());
            return rawResponse;
        }
    }

    /**
     * Apply mapping based on template configuration
     */
    private Map<String, Object> applyMapping(Map<String, Object> rawData, Map<String, Object> template) {
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : template.entrySet()) {
            String targetField = entry.getKey();
            Object mappingConfig = entry.getValue();

            if (mappingConfig instanceof String) {
                // Simple JSON path mapping
                String jsonPath = (String) mappingConfig;
                Object value = extractValueByPath(rawData, jsonPath);
                if (value != null) {
                    result.put(targetField, value);
                }
            } else if (mappingConfig instanceof Map) {
                // Complex mapping configuration or nested structure
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) mappingConfig;

                // Check if this is a nested structure (like hits.total, hits.hits)
                if (config.containsKey("path") || config.containsKey("type") || config.containsKey("default")) {
                    // This is a mapping configuration
                    Object value = applyComplexMapping(rawData, config);
                    if (value != null) {
                        result.put(targetField, value);
                    }
                } else {
                    // This is a nested structure, recursively apply mapping
                    Map<String, Object> nestedResult = applyMapping(rawData, config);
                    if (!nestedResult.isEmpty()) {
                        result.put(targetField, nestedResult);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Apply complex mapping with transformations
     */
    private Object applyComplexMapping(Map<String, Object> rawData, Map<String, Object> config) {
        String path = (String) config.get("path");
        String type = (String) config.get("type");
        Object defaultValue = config.get("default");

        if (path == null) {
            return defaultValue;
        }

        Object value = extractValueByPath(rawData, path);

        if (value == null) {
            return defaultValue;
        }

        // Apply type transformations
        if (type != null) {
            value = transformValue(value, type);
        }

        return value;
    }

    /**
     * Extract value from nested map using JSON path notation
     */
    private Object extractValueByPath(Map<String, Object> data, String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        // Handle simple field access
        if (!path.contains(".") && !path.contains("[")) {
            return data.get(path);
        }

        // Split path and navigate
        String[] parts = path.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            // Handle array access like "hits[0]"
            if (part.contains("[") && part.contains("]")) {
                String fieldName = part.substring(0, part.indexOf('['));
                String indexStr = part.substring(part.indexOf('[') + 1, part.indexOf(']'));

                if (current instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) current;
                    current = map.get(fieldName);
                }

                if (current instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) current;
                    try {
                        int index = Integer.parseInt(indexStr);
                        if (index >= 0 && index < list.size()) {
                            current = list.get(index);
                        } else {
                            return null;
                        }
                    } catch (NumberFormatException e) {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                // Simple field access
                if (current instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) current;
                    current = map.get(part);
                } else {
                    return null;
                }
            }
        }

        return current;
    }

    /**
     * Transform value to specified type
     */
    private Object transformValue(Object value, String type) {
        if (value == null) {
            return null;
        }

        try {
            switch (type.toLowerCase(Locale.ROOT)) {
                case "string":
                    return value.toString();
                case "integer":
                case "int":
                    if (value instanceof Number) {
                        return ((Number) value).intValue();
                    }
                    return Integer.parseInt(value.toString());
                case "long":
                    if (value instanceof Number) {
                        return ((Number) value).longValue();
                    }
                    return Long.parseLong(value.toString());
                case "double":
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                    return Double.parseDouble(value.toString());
                case "boolean":
                    if (value instanceof Boolean) {
                        return value;
                    }
                    return Boolean.parseBoolean(value.toString());
                default:
                    return value;
            }
        } catch (Exception e) {
            log.warn("Failed to transform value {} to type {}: {}", value, type, e.getMessage());
            return value;
        }
    }

    /**
     * Map common search response formats to OpenSearch format
     */
    private String mapCommonFormat(Map<String, Object> rawData) {
        try {
            Map<String, Object> opensearchFormat = new HashMap<>();

            // Extract hits
            Object resultsObj = rawData.get("results");
            if (resultsObj == null) {
                resultsObj = rawData.get("documents");
            }

            List<Map<String, Object>> hits = new ArrayList<>();
            int totalHits = 0;

            if (resultsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> results = (List<Object>) resultsObj;
                totalHits = results.size();

                for (int i = 0; i < results.size(); i++) {
                    Object item = results.get(i);
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> doc = (Map<String, Object>) item;

                        Map<String, Object> hit = new HashMap<>();
                        hit.put("_index", "remote");
                        hit.put("_id", doc.getOrDefault("id", String.valueOf(i)));
                        hit.put("_score", doc.getOrDefault("score", 1.0));
                        hit.put("_source", doc);

                        hits.add(hit);
                    }
                }
            }

            // Build OpenSearch response structure
            Map<String, Object> total = new HashMap<>();
            total.put("value", totalHits);
            total.put("relation", "eq");

            Map<String, Object> hitsContainer = new HashMap<>();
            hitsContainer.put("total", total);
            hitsContainer.put("max_score", hits.isEmpty() ? null : 1.0);
            hitsContainer.put("hits", hits);

            opensearchFormat.put("hits", hitsContainer);
            opensearchFormat.put("took", rawData.getOrDefault("took", 1));
            opensearchFormat.put("timed_out", false);

            return mapToJson(opensearchFormat);

        } catch (Exception e) {
            log.error("Failed to map common format: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Wrap unknown format in basic OpenSearch structure
     */
    private String wrapInBasicFormat(Map<String, Object> rawData) {
        try {
            Map<String, Object> hit = new HashMap<>();
            hit.put("_index", "remote");
            hit.put("_id", "1");
            hit.put("_score", 1.0);
            hit.put("_source", rawData);

            Map<String, Object> total = new HashMap<>();
            total.put("value", 1);
            total.put("relation", "eq");

            Map<String, Object> hitsContainer = new HashMap<>();
            hitsContainer.put("total", total);
            hitsContainer.put("max_score", 1.0);
            hitsContainer.put("hits", List.of(hit));

            Map<String, Object> opensearchFormat = new HashMap<>();
            opensearchFormat.put("hits", hitsContainer);
            opensearchFormat.put("took", 1);
            opensearchFormat.put("timed_out", false);

            return mapToJson(opensearchFormat);

        } catch (Exception e) {
            log.error("Failed to wrap in basic format: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Parse JSON string to Map
     */
    private Map<String, Object> parseJsonToMap(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }

        // Simple approach: just remove all newlines and extra whitespace
        String cleanJson = json.replaceAll("\\s+", " ").trim();

        try (XContentParser parser = XContentFactory.jsonBuilder().contentType().xContent().createParser(null, null, cleanJson)) {
            return parser.map();
        }
    }

    /**
     * Convert Map to JSON string
     */
    private String mapToJson(Map<String, Object> map) throws Exception {
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
            builder.map(map);
            return builder.toString();
        }
    }

    /**
     * Create empty response in OpenSearch format
     */
    private String createEmptyResponse() {
        try {
            Map<String, Object> total = new HashMap<>();
            total.put("value", 0);
            total.put("relation", "eq");

            Map<String, Object> hitsContainer = new HashMap<>();
            hitsContainer.put("total", total);
            hitsContainer.put("max_score", null);
            hitsContainer.put("hits", List.of());

            Map<String, Object> response = new HashMap<>();
            response.put("hits", hitsContainer);
            response.put("took", 0);
            response.put("timed_out", false);

            return mapToJson(response);
        } catch (Exception e) {
            return "{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"hits\":[]}}";
        }
    }

    /**
     * Create error response
     */
    private String createErrorResponse(String errorMessage) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("type", "remote_mapping_exception");
            error.put("reason", errorMessage);

            Map<String, Object> response = new HashMap<>();
            response.put("error", error);

            return mapToJson(response);
        } catch (Exception e) {
            return "{\"error\":{\"type\":\"remote_mapping_exception\",\"reason\":\"" + errorMessage.replace("\"", "\\\"") + "\"}}";
        }
    }
}
