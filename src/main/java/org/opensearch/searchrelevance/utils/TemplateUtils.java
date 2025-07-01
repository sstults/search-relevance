/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for template variable substitution in LLM prompt templates.
 */
public class TemplateUtils {
    private static final Logger LOGGER = LogManager.getLogger(TemplateUtils.class);

    // Pattern to match template variables in format {variableName}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    // Standard template variables for judgment generation
    public static final String VAR_SEARCH_TEXT = "searchText";
    public static final String VAR_REFERENCE = "reference";
    public static final String VAR_HITS = "hits";

    private TemplateUtils() {
        // Utility class
    }

    /**
     * Substitutes variables in a template string with provided values.
     * Variables are expected in the format {variableName}.
     *
     * @param template The template string containing variables
     * @param variables Map of variable names to their values
     * @return The template with variables substituted
     */
    public static String substituteVariables(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        if (variables == null || variables.isEmpty()) {
            LOGGER.warn("No variables provided for template substitution");
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variables.get(variableName);

            if (replacement != null) {
                // Escape special regex characters in replacement
                replacement = Matcher.quoteReplacement(replacement);
                matcher.appendReplacement(result, replacement);
                LOGGER.debug("Substituted variable '{}' in template", variableName);
            } else {
                LOGGER.warn("Variable '{}' not found in provided variables, leaving unchanged", variableName);
                // Leave the variable placeholder unchanged
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Validates that a template contains only supported variables.
     *
     * @param template The template to validate
     * @return true if template contains only supported variables, false otherwise
     */
    public static boolean validateTemplate(String template) {
        if (template == null || template.isEmpty()) {
            return true;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (!isSupportedVariable(variableName)) {
                LOGGER.warn("Unsupported variable '{}' found in template", variableName);
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if a variable name is supported for judgment templates.
     *
     * @param variableName The variable name to check
     * @return true if the variable is supported
     */
    public static boolean isSupportedVariable(String variableName) {
        return VAR_SEARCH_TEXT.equals(variableName) || VAR_REFERENCE.equals(variableName) || VAR_HITS.equals(variableName);
    }

    /**
     * Creates a variables map for judgment template substitution.
     *
     * @param searchText The search query text
     * @param reference The reference answer (can be null)
     * @param hits The formatted hits JSON string
     * @return Map of variables for template substitution
     */
    public static Map<String, String> createJudgmentVariables(String searchText, String reference, String hits) {
        Map<String, String> variables = Map.of(
            VAR_SEARCH_TEXT,
            searchText != null ? searchText : "",
            VAR_REFERENCE,
            reference != null ? reference : "",
            VAR_HITS,
            hits != null ? hits : ""
        );

        LOGGER.debug(
            "Created judgment variables: searchText={}, reference={}, hits.length={}",
            searchText,
            reference != null ? "provided" : "null",
            hits != null ? hits.length() : 0
        );

        return variables;
    }
}
