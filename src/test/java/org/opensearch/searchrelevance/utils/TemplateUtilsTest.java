/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class TemplateUtilsTest {

    @Test
    public void testSubstituteVariables_BasicSubstitution() {
        String template = "Search for {searchText} in {hits}";
        Map<String, String> variables = Map.of("searchText", "laptop", "hits", "[{\"id\":\"1\",\"title\":\"Gaming Laptop\"}]");

        String result = TemplateUtils.substituteVariables(template, variables);
        assertEquals("Search for laptop in [{\"id\":\"1\",\"title\":\"Gaming Laptop\"}]", result);
    }

    @Test
    public void testSubstituteVariables_WithReference() {
        String template = "Query: {searchText}\nReference: {reference}\nResults: {hits}";
        Map<String, String> variables = Map.of(
            "searchText",
            "best laptops",
            "reference",
            "High-performance gaming laptops",
            "hits",
            "[{\"id\":\"1\",\"title\":\"Gaming Laptop\"}]"
        );

        String result = TemplateUtils.substituteVariables(template, variables);
        String expected =
            "Query: best laptops\nReference: High-performance gaming laptops\nResults: [{\"id\":\"1\",\"title\":\"Gaming Laptop\"}]";
        assertEquals(expected, result);
    }

    @Test
    public void testSubstituteVariables_MissingVariable() {
        String template = "Search for {searchText} with {missingVar}";
        Map<String, String> variables = Map.of("searchText", "laptop");

        String result = TemplateUtils.substituteVariables(template, variables);
        assertEquals("Search for laptop with {missingVar}", result);
    }

    @Test
    public void testSubstituteVariables_EmptyTemplate() {
        String result = TemplateUtils.substituteVariables("", Map.of("searchText", "test"));
        assertEquals("", result);
    }

    @Test
    public void testSubstituteVariables_NullTemplate() {
        String result = TemplateUtils.substituteVariables(null, Map.of("searchText", "test"));
        assertEquals(null, result);
    }

    @Test
    public void testSubstituteVariables_EmptyVariables() {
        String template = "Search for {searchText}";
        String result = TemplateUtils.substituteVariables(template, Map.of());
        assertEquals("Search for {searchText}", result);
    }

    @Test
    public void testSubstituteVariables_SpecialCharacters() {
        String template = "Query: {searchText}";
        Map<String, String> variables = Map.of("searchText", "test$with\\special[chars]");

        String result = TemplateUtils.substituteVariables(template, variables);
        assertEquals("Query: test$with\\special[chars]", result);
    }

    @Test
    public void testValidateTemplate_ValidTemplate() {
        String template = "Search: {searchText}, Reference: {reference}, Hits: {hits}";
        assertTrue(TemplateUtils.validateTemplate(template));
    }

    @Test
    public void testValidateTemplate_InvalidVariable() {
        String template = "Search: {searchText}, Invalid: {invalidVar}";
        assertFalse(TemplateUtils.validateTemplate(template));
    }

    @Test
    public void testValidateTemplate_EmptyTemplate() {
        assertTrue(TemplateUtils.validateTemplate(""));
    }

    @Test
    public void testValidateTemplate_NullTemplate() {
        assertTrue(TemplateUtils.validateTemplate(null));
    }

    @Test
    public void testValidateTemplate_NoVariables() {
        assertTrue(TemplateUtils.validateTemplate("This is a plain template without variables"));
    }

    @Test
    public void testIsSupportedVariable() {
        assertTrue(TemplateUtils.isSupportedVariable("searchText"));
        assertTrue(TemplateUtils.isSupportedVariable("reference"));
        assertTrue(TemplateUtils.isSupportedVariable("hits"));
        assertFalse(TemplateUtils.isSupportedVariable("invalidVar"));
        assertFalse(TemplateUtils.isSupportedVariable(""));
        assertFalse(TemplateUtils.isSupportedVariable(null));
    }

    @Test
    public void testCreateJudgmentVariables() {
        String searchText = "best laptops";
        String reference = "gaming laptops";
        String hits = "[{\"id\":\"1\"}]";

        Map<String, String> variables = TemplateUtils.createJudgmentVariables(searchText, reference, hits);

        assertEquals(searchText, variables.get("searchText"));
        assertEquals(reference, variables.get("reference"));
        assertEquals(hits, variables.get("hits"));
    }

    @Test
    public void testCreateJudgmentVariables_NullValues() {
        Map<String, String> variables = TemplateUtils.createJudgmentVariables(null, null, null);

        assertEquals("", variables.get("searchText"));
        assertEquals("", variables.get("reference"));
        assertEquals("", variables.get("hits"));
    }

    @Test
    public void testCreateJudgmentVariables_MixedNullValues() {
        String searchText = "test query";
        String hits = "[{\"id\":\"1\"}]";

        Map<String, String> variables = TemplateUtils.createJudgmentVariables(searchText, null, hits);

        assertEquals(searchText, variables.get("searchText"));
        assertEquals("", variables.get("reference"));
        assertEquals(hits, variables.get("hits"));
    }

    @Test
    public void testComplexTemplate() {
        String template = "You are an expert evaluator. "
            + "Rate the relevance of search results for query: '{searchText}'. "
            + "Reference answer: '{reference}'. "
            + "Search results: {hits}. "
            + "Provide ratings from 0.0 to 1.0.";

        Map<String, String> variables = Map.of(
            "searchText",
            "machine learning algorithms",
            "reference",
            "supervised and unsupervised learning methods",
            "hits",
            "[{\"id\":\"doc1\",\"title\":\"ML Basics\"},{\"id\":\"doc2\",\"title\":\"Deep Learning\"}]"
        );

        String result = TemplateUtils.substituteVariables(template, variables);

        assertTrue(result.contains("machine learning algorithms"));
        assertTrue(result.contains("supervised and unsupervised learning methods"));
        assertTrue(result.contains("ML Basics"));
        assertTrue(result.contains("Deep Learning"));
        assertFalse(result.contains("{searchText}"));
        assertFalse(result.contains("{reference}"));
        assertFalse(result.contains("{hits}"));
    }
}
