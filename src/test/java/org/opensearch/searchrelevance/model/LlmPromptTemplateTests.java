/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.test.OpenSearchTestCase;

/**
 * Unit tests for LlmPromptTemplate model
 */
public class LlmPromptTemplateTests extends OpenSearchTestCase {

    public void testLlmPromptTemplateCreation() {
        String templateId = "test-template-1";
        String name = "Test Template";
        String description = "A test template for unit testing";
        String template = "Rate the relevance of this document: {document} to query: {query}";
        Long createdTime = System.currentTimeMillis();
        Long lastUpdatedTime = createdTime + 1000;

        LlmPromptTemplate llmTemplate = new LlmPromptTemplate(templateId, name, description, template, createdTime, lastUpdatedTime);

        assertEquals(templateId, llmTemplate.getTemplateId());
        assertEquals(name, llmTemplate.getName());
        assertEquals(description, llmTemplate.getDescription());
        assertEquals(template, llmTemplate.getTemplate());
        assertEquals(createdTime, llmTemplate.getCreatedTime());
        assertEquals(lastUpdatedTime, llmTemplate.getLastUpdatedTime());
    }

    public void testLlmPromptTemplateWithNullOptionalFields() {
        String templateId = "test-template-2";
        String name = "Test Template 2";
        String template = "Simple template without description";

        LlmPromptTemplate llmTemplate = new LlmPromptTemplate(templateId, name, null, template, null, null);

        assertEquals(templateId, llmTemplate.getTemplateId());
        assertEquals(name, llmTemplate.getName());
        assertNull(llmTemplate.getDescription());
        assertEquals(template, llmTemplate.getTemplate());
        assertNull(llmTemplate.getCreatedTime());
        assertNull(llmTemplate.getLastUpdatedTime());
    }

    public void testLlmPromptTemplateSerialization() throws IOException {
        String templateId = "test-template-3";
        String name = "Serialization Test";
        String description = "Testing serialization";
        String template = "Template content for serialization test";
        Long createdTime = 1640995200000L; // Fixed timestamp for testing
        Long lastUpdatedTime = 1640995260000L;

        LlmPromptTemplate original = new LlmPromptTemplate(templateId, name, description, template, createdTime, lastUpdatedTime);

        // Test stream serialization
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        LlmPromptTemplate deserialized = new LlmPromptTemplate(input);

        assertEquals(original.getTemplateId(), deserialized.getTemplateId());
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getDescription(), deserialized.getDescription());
        assertEquals(original.getTemplate(), deserialized.getTemplate());
        assertEquals(original.getCreatedTime(), deserialized.getCreatedTime());
        assertEquals(original.getLastUpdatedTime(), deserialized.getLastUpdatedTime());
    }

    public void testLlmPromptTemplateXContentSerialization() throws IOException {
        String templateId = "test-template-4";
        String name = "XContent Test";
        String description = "Testing XContent serialization";
        String template = "XContent template: {query} -> {document}";
        Long createdTime = 1640995200000L;
        Long lastUpdatedTime = 1640995260000L;

        LlmPromptTemplate original = new LlmPromptTemplate(templateId, name, description, template, createdTime, lastUpdatedTime);

        // Test XContent serialization
        XContentBuilder builder = XContentFactory.jsonBuilder();
        original.toXContent(builder, null);

        XContentParser parser = createParser(builder);
        LlmPromptTemplate parsed = LlmPromptTemplate.parse(parser);

        assertEquals(original.getTemplateId(), parsed.getTemplateId());
        assertEquals(original.getName(), parsed.getName());
        assertEquals(original.getDescription(), parsed.getDescription());
        assertEquals(original.getTemplate(), parsed.getTemplate());
        assertEquals(original.getCreatedTime(), parsed.getCreatedTime());
        assertEquals(original.getLastUpdatedTime(), parsed.getLastUpdatedTime());
    }

    public void testLlmPromptTemplateXContentSerializationWithNulls() throws IOException {
        String templateId = "test-template-5";
        String name = "Null Fields Test";
        String template = "Template with null optional fields";

        LlmPromptTemplate original = new LlmPromptTemplate(templateId, name, null, template, null, null);

        // Test XContent serialization with null fields
        XContentBuilder builder = XContentFactory.jsonBuilder();
        original.toXContent(builder, null);

        XContentParser parser = createParser(builder);
        LlmPromptTemplate parsed = LlmPromptTemplate.parse(parser);

        assertEquals(original.getTemplateId(), parsed.getTemplateId());
        assertEquals(original.getName(), parsed.getName());
        assertNull(parsed.getDescription());
        assertEquals(original.getTemplate(), parsed.getTemplate());
        assertNull(parsed.getCreatedTime());
        assertNull(parsed.getLastUpdatedTime());
    }

    public void testLlmPromptTemplateEqualsAndHashCode() {
        String templateId = "test-template-6";
        String name = "Equals Test";
        String description = "Testing equals and hashCode";
        String template = "Template for equals testing";
        Long createdTime = 1640995200000L;
        Long lastUpdatedTime = 1640995260000L;

        LlmPromptTemplate template1 = new LlmPromptTemplate(templateId, name, description, template, createdTime, lastUpdatedTime);

        LlmPromptTemplate template2 = new LlmPromptTemplate(templateId, name, description, template, createdTime, lastUpdatedTime);

        LlmPromptTemplate template3 = new LlmPromptTemplate("different-id", name, description, template, createdTime, lastUpdatedTime);

        // Test equals
        assertEquals(template1, template2);
        assertNotEquals(template1, template3);
        assertNotEquals(template1, null);
        assertNotEquals(template1, "not a template");

        // Test hashCode
        assertEquals(template1.hashCode(), template2.hashCode());
        assertNotEquals(template1.hashCode(), template3.hashCode());
    }

    public void testLlmPromptTemplateToString() {
        String templateId = "test-template-7";
        String name = "ToString Test";
        String description = "Testing toString method";
        String template = "Template for toString testing";
        Long createdTime = 1640995200000L;
        Long lastUpdatedTime = 1640995260000L;

        LlmPromptTemplate llmTemplate = new LlmPromptTemplate(templateId, name, description, template, createdTime, lastUpdatedTime);

        String toString = llmTemplate.toString();

        assertTrue(toString.contains(templateId));
        assertTrue(toString.contains(name));
        assertTrue(toString.contains(description));
        assertTrue(toString.contains(template));
        assertTrue(toString.contains(createdTime.toString()));
        assertTrue(toString.contains(lastUpdatedTime.toString()));
    }
}
