/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.llmprompttemplate;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;

/**
 * Integration tests for LLM Prompt Template functionality
 */
public class LlmPromptTemplateIT extends BaseSearchRelevanceIT {

    private static final String LLM_PROMPT_TEMPLATE_ENDPOINT = "/_plugins/_search_relevance/llm_prompt_templates";

    public void testCreateAndRetrieveLlmPromptTemplate() throws IOException {
        String templateId = "test-template-1";
        String templateName = "Relevance Rating Template";
        String templateDescription = "Template for rating document relevance";
        String templateContent = "Rate the relevance of this document: {document} to the query: {query}. Provide a score from 0-4.";

        // Create template
        XContentBuilder templateBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("name", templateName)
            .field("description", templateDescription)
            .field("template", templateContent)
            .endObject();

        Request putRequest = new Request("PUT", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        putRequest.setJsonEntity(templateBuilder.toString());

        Response putResponse = client().performRequest(putRequest);
        assertEquals(200, putResponse.getStatusLine().getStatusCode());

        // Refresh index to ensure document is searchable
        client().performRequest(new Request("POST", "/_refresh"));

        // Retrieve template
        Request getRequest = new Request("GET", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        Response getResponse = client().performRequest(getRequest);
        assertEquals(200, getResponse.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponseToMap(getResponse);
        assertTrue((Boolean) responseMap.get("found"));

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) responseMap.get("template");
        assertEquals(templateId, template.get("template_id"));
        assertEquals(templateName, template.get("name"));
        assertEquals(templateDescription, template.get("description"));
        assertEquals(templateContent, template.get("template"));
        assertNotNull(template.get("created_time"));
        assertNotNull(template.get("last_updated_time"));
    }

    public void testUpdateLlmPromptTemplate() throws IOException {
        String templateId = "test-template-2";
        String originalName = "Original Template";
        String updatedName = "Updated Template";
        String templateContent = "Original template content";

        // Create original template
        XContentBuilder originalBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("name", originalName)
            .field("template", templateContent)
            .endObject();

        Request putRequest = new Request("PUT", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        putRequest.setJsonEntity(originalBuilder.toString());
        client().performRequest(putRequest);

        // Update template
        XContentBuilder updatedBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("name", updatedName)
            .field("template", templateContent)
            .endObject();

        putRequest.setJsonEntity(updatedBuilder.toString());
        Response updateResponse = client().performRequest(putRequest);
        assertEquals(200, updateResponse.getStatusLine().getStatusCode());

        // Refresh and verify update
        client().performRequest(new Request("POST", "/_refresh"));

        Request getRequest = new Request("GET", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        Response getResponse = client().performRequest(getRequest);

        Map<String, Object> responseMap = parseResponseToMap(getResponse);
        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) responseMap.get("template");
        assertEquals(updatedName, template.get("name"));
    }

    public void testDeleteLlmPromptTemplate() throws IOException {
        String templateId = "test-template-3";
        String templateName = "Template to Delete";
        String templateContent = "This template will be deleted";

        // Create template
        XContentBuilder templateBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("name", templateName)
            .field("template", templateContent)
            .endObject();

        Request putRequest = new Request("PUT", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        putRequest.setJsonEntity(templateBuilder.toString());
        client().performRequest(putRequest);

        // Refresh to ensure document is indexed
        client().performRequest(new Request("POST", "/_refresh"));

        // Delete template
        Request deleteRequest = new Request("DELETE", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        Response deleteResponse = client().performRequest(deleteRequest);
        assertEquals(200, deleteResponse.getStatusLine().getStatusCode());

        Map<String, Object> deleteResponseMap = parseResponseToMap(deleteResponse);
        assertEquals(templateId, deleteResponseMap.get("template_id"));
        assertEquals("deleted", deleteResponseMap.get("result"));
        assertTrue((Boolean) deleteResponseMap.get("found"));

        // Refresh and verify deletion
        client().performRequest(new Request("POST", "/_refresh"));

        Request getRequest = new Request("GET", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        try {
            client().performRequest(getRequest);
            fail("Expected 404 when retrieving deleted template");
        } catch (Exception e) {
            // Expected 404 error for deleted template
            assertTrue(e.getMessage().contains("404") || e.getMessage().contains("Not Found"));
        }
    }

    public void testSearchLlmPromptTemplates() throws IOException {
        // Create multiple templates
        String[] templateIds = { "search-test-1", "search-test-2", "search-test-3" };
        String[] templateNames = { "Search Template 1", "Search Template 2", "Search Template 3" };

        for (int i = 0; i < templateIds.length; i++) {
            XContentBuilder templateBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .field("name", templateNames[i])
                .field("template", "Template content " + (i + 1))
                .endObject();

            Request putRequest = new Request("PUT", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateIds[i]);
            putRequest.setJsonEntity(templateBuilder.toString());
            client().performRequest(putRequest);
        }

        // Refresh to ensure all documents are indexed
        client().performRequest(new Request("POST", "/_refresh"));

        // Search templates
        Request searchRequest = new Request("GET", LLM_PROMPT_TEMPLATE_ENDPOINT + "/_search");
        Response searchResponse = client().performRequest(searchRequest);
        assertEquals(200, searchResponse.getStatusLine().getStatusCode());

        Map<String, Object> searchResponseMap = parseResponseToMap(searchResponse);
        @SuppressWarnings("unchecked")
        Map<String, Object> hits = (Map<String, Object>) searchResponseMap.get("hits");

        // Handle both old format (Number) and new format (Object with value field)
        Object totalObj = hits.get("total");
        int totalHits;
        if (totalObj instanceof Number) {
            totalHits = ((Number) totalObj).intValue();
        } else if (totalObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> totalMap = (Map<String, Object>) totalObj;
            totalHits = ((Number) totalMap.get("value")).intValue();
        } else {
            fail("Unexpected total hits format: " + totalObj);
            return;
        }

        assertTrue(totalHits >= templateIds.length);
    }

    public void testLlmPromptTemplateValidation() throws IOException {
        String templateId = "validation-test";

        // Test missing required fields
        XContentBuilder invalidBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("description", "Missing name and template")
            .endObject();

        Request putRequest = new Request("PUT", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        putRequest.setJsonEntity(invalidBuilder.toString());

        try {
            client().performRequest(putRequest);
            fail("Expected validation error for missing required fields");
        } catch (Exception e) {
            // Expected validation error
            assertTrue(e.getMessage().contains("400") || e.getMessage().contains("Bad Request"));
        }
    }

    public void testLlmPromptTemplateWithJudgmentIntegration() throws IOException {
        // This test verifies that LLM templates can be used with judgment processing
        String templateId = "judgment-template";
        String templateName = "Judgment Rating Template";
        String templateContent = "Rate the relevance of document '{document}' to query '{query}' on a scale of 0-4 where:\n"
            + "0 = Not relevant\n"
            + "1 = Slightly relevant\n"
            + "2 = Moderately relevant\n"
            + "3 = Highly relevant\n"
            + "4 = Perfectly relevant\n"
            + "Provide only the numeric score.";

        // Create judgment-specific template
        XContentBuilder templateBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("name", templateName)
            .field("description", "Template for LLM-based relevance judgments")
            .field("template", templateContent)
            .endObject();

        Request putRequest = new Request("PUT", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        putRequest.setJsonEntity(templateBuilder.toString());

        Response putResponse = client().performRequest(putRequest);
        assertEquals(200, putResponse.getStatusLine().getStatusCode());

        // Refresh and verify template can be retrieved for judgment processing
        client().performRequest(new Request("POST", "/_refresh"));

        Request getRequest = new Request("GET", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        Response getResponse = client().performRequest(getRequest);
        assertEquals(200, getResponse.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponseToMap(getResponse);
        assertTrue((Boolean) responseMap.get("found"));

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) responseMap.get("template");

        // Verify template structure is suitable for judgment processing
        String retrievedTemplate = (String) template.get("template");
        assertTrue(retrievedTemplate.contains("{document}"));
        assertTrue(retrievedTemplate.contains("{query}"));
        assertTrue(retrievedTemplate.contains("0-4"));
    }

    private Map<String, Object> parseResponseToMap(Response response) throws IOException {
        try {
            return XContentHelper.convertToMap(XContentType.JSON.xContent(), EntityUtils.toString(response.getEntity()), false);
        } catch (ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }
}
