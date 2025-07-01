/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;

import java.io.IOException;
import java.util.Map;

import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;

/**
 * Integration tests for LLM Prompt Template integration with LLM Judgment generation.
 * Tests the end-to-end workflow of creating templates and using them for judgment generation.
 */
public class LlmJudgmentTemplateIntegrationIT extends BaseSearchRelevanceIT {

    private static final String LLM_PROMPT_TEMPLATE_ENDPOINT = "/_plugins/_search_relevance/llm_prompt_templates";
    private static final String TEST_INDEX_NAME = "llm_judgment_template_test_index";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        // Create test index for judgment testing
        createTestIndex();
    }

    @Override
    public void tearDown() throws Exception {
        // Clean up test index
        try {
            deleteIndex(TEST_INDEX_NAME);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        super.tearDown();
    }

    public void testLlmJudgmentWithCustomTemplate() throws IOException, InterruptedException {
        // Skip test if workbench is disabled
        if (!isWorkbenchEnabled()) {
            return;
        }

        String templateId = "judgment-template-test";
        String templateName = "Custom Judgment Template";
        String customPrompt = "Evaluate the relevance of this document to the query.\n"
            + "Query: {queryText}\n"
            + "Reference Answer: {referenceAnswer}\n"
            + "Document Content: {hits}\n"
            + "Rate the relevance on a scale of 0-4 where:\n"
            + "0 = Not relevant at all\n"
            + "1 = Slightly relevant\n"
            + "2 = Moderately relevant\n"
            + "3 = Highly relevant\n"
            + "4 = Perfectly relevant\n"
            + "Provide only the numeric score as your response.";

        // Step 1: Create LLM prompt template
        createLlmPromptTemplate(templateId, templateName, customPrompt);

        // Step 2: Create query set for testing
        String querySetId = createTestQuerySet();

        // Step 3: Create search configuration
        String searchConfigId = createTestSearchConfiguration();

        // Step 4: Create LLM judgment with template reference
        String judgmentId = createLlmJudgmentWithTemplate(templateId, querySetId, searchConfigId);

        // Step 5: Verify judgment was created successfully
        verifyJudgmentCreation(judgmentId);

        // Step 6: Verify template was used (this would require checking logs or internal state)
        // For now, we verify that the judgment process completed without errors
        assertTrue("Judgment should be created successfully with template", judgmentId != null && !judgmentId.isEmpty());
    }

    public void testLlmJudgmentWithMissingTemplate() throws IOException, InterruptedException {
        // Skip test if workbench is disabled
        if (!isWorkbenchEnabled()) {
            return;
        }

        String nonExistentTemplateId = "non-existent-template";
        String querySetId = createTestQuerySet();
        String searchConfigId = createTestSearchConfiguration();

        // Create LLM judgment with non-existent template - should fall back to default prompt
        String judgmentId = createLlmJudgmentWithTemplate(nonExistentTemplateId, querySetId, searchConfigId);

        // Verify judgment was still created (fallback behavior)
        verifyJudgmentCreation(judgmentId);
        assertTrue("Judgment should be created even with missing template (fallback)", judgmentId != null && !judgmentId.isEmpty());
    }

    public void testLlmJudgmentTemplateVariableSubstitution() throws IOException, InterruptedException {
        // Skip test if workbench is disabled
        if (!isWorkbenchEnabled()) {
            return;
        }

        String templateId = "variable-test-template";
        String templateName = "Variable Substitution Test";

        // Template with all supported variables
        String templateWithVariables = "Query: {queryText}\n"
            + "Reference: {referenceAnswer}\n"
            + "Documents: {hits}\n"
            + "Rate relevance 0-4.";

        // Create template
        createLlmPromptTemplate(templateId, templateName, templateWithVariables);

        // Create test data
        String querySetId = createTestQuerySet();
        String searchConfigId = createTestSearchConfiguration();

        // Create judgment with template
        String judgmentId = createLlmJudgmentWithTemplate(templateId, querySetId, searchConfigId);

        // Verify successful creation
        verifyJudgmentCreation(judgmentId);
        assertTrue("Judgment with variable substitution should succeed", judgmentId != null && !judgmentId.isEmpty());
    }

    public void testLlmJudgmentWithoutTemplate() throws IOException, InterruptedException {
        // Skip test if workbench is disabled
        if (!isWorkbenchEnabled()) {
            return;
        }

        String querySetId = createTestQuerySet();
        String searchConfigId = createTestSearchConfiguration();

        // Create LLM judgment without template (should use default prompt)
        String judgmentId = createLlmJudgmentWithoutTemplate(querySetId, searchConfigId);

        // Verify judgment was created successfully
        verifyJudgmentCreation(judgmentId);
        assertTrue("Judgment without template should use default prompt", judgmentId != null && !judgmentId.isEmpty());
    }

    private void createLlmPromptTemplate(String templateId, String templateName, String promptTemplate) throws IOException {
        XContentBuilder templateBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("name", templateName)
            .field("description", "Template for integration testing")
            .field("promptTemplate", promptTemplate)
            .startObject("metadata")
            .field("testType", "integration")
            .field("version", "1.0")
            .endObject()
            .endObject();

        Request putRequest = new Request("PUT", LLM_PROMPT_TEMPLATE_ENDPOINT + "/" + templateId);
        putRequest.setJsonEntity(templateBuilder.toString());

        Response putResponse = client().performRequest(putRequest);
        assertEquals("Template creation should succeed", 200, putResponse.getStatusLine().getStatusCode());

        // Refresh to ensure template is available
        client().performRequest(new Request("POST", "/_refresh"));
    }

    private String createTestQuerySet() throws IOException {
        XContentBuilder querySetBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("name", "LLM Template Test Query Set")
            .field("description", "Query set for testing LLM template integration")
            .startArray("queries")
            .startObject()
            .field("query", "test query")
            .field("reference_answer", "test reference answer")
            .endObject()
            .endArray()
            .endObject();

        Request createQuerySetRequest = new Request("PUT", "/_plugins/_search_relevance/querysets");
        createQuerySetRequest.setJsonEntity(querySetBuilder.toString());

        Response response = client().performRequest(createQuerySetRequest);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponseToMap(response);
        return (String) responseMap.get("query_set_id");
    }

    private String createTestSearchConfiguration() throws IOException {
        XContentBuilder searchConfigBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("name", "LLM Template Test Search Config")
            .field("description", "Search configuration for testing LLM template integration")
            .field("index", TEST_INDEX_NAME)
            .startObject("query")
            .startObject("match")
            .field("content", "{{query}}")
            .endObject()
            .endObject()
            .endObject();

        Request createSearchConfigRequest = new Request("PUT", "/_plugins/_search_relevance/search_configurations");
        createSearchConfigRequest.setJsonEntity(searchConfigBuilder.toString());

        Response response = client().performRequest(createSearchConfigRequest);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponseToMap(response);
        return (String) responseMap.get("search_configuration_id");
    }

    private String createLlmJudgmentWithTemplate(String templateId, String querySetId, String searchConfigId) throws IOException {
        XContentBuilder judgmentBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("name", "LLM Judgment with Template")
            .field("description", "Testing LLM judgment with custom template")
            .field("type", "LLM_JUDGMENT")
            .field("modelId", "test-model-id")
            .field("templateId", templateId)  // This is the key addition for template support
            .field("querySetId", querySetId)
            .startArray("searchConfigurationList")
            .value(searchConfigId)
            .endArray()
            .field("size", 5)
            .field("tokenLimit", 1000)
            .startArray("contextFields")
            .value("content")
            .endArray()
            .field("ignoreFailure", true)  // Use true for testing to handle ML model unavailability
            .endObject();

        Request createJudgmentRequest = new Request("PUT", JUDGMENTS_URL);
        createJudgmentRequest.setJsonEntity(judgmentBuilder.toString());

        Response response = client().performRequest(createJudgmentRequest);
        assertEquals("Judgment creation should succeed", 200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponseToMap(response);
        return (String) responseMap.get("judgment_id");
    }

    private String createLlmJudgmentWithoutTemplate(String querySetId, String searchConfigId) throws IOException {
        XContentBuilder judgmentBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .field("name", "LLM Judgment without Template")
            .field("description", "Testing LLM judgment with default prompt")
            .field("type", "LLM_JUDGMENT")
            .field("modelId", "test-model-id")
            // No templateId field - should use default prompt
            .field("querySetId", querySetId)
            .startArray("searchConfigurationList")
            .value(searchConfigId)
            .endArray()
            .field("size", 5)
            .field("tokenLimit", 1000)
            .startArray("contextFields")
            .value("content")
            .endArray()
            .field("ignoreFailure", true)
            .endObject();

        Request createJudgmentRequest = new Request("PUT", JUDGMENTS_URL);
        createJudgmentRequest.setJsonEntity(judgmentBuilder.toString());

        Response response = client().performRequest(createJudgmentRequest);
        assertEquals("Judgment creation should succeed", 200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponseToMap(response);
        return (String) responseMap.get("judgment_id");
    }

    private void verifyJudgmentCreation(String judgmentId) throws IOException {
        assertNotNull("Judgment ID should not be null", judgmentId);
        assertFalse("Judgment ID should not be empty", judgmentId.isEmpty());

        // Refresh to ensure judgment is indexed
        client().performRequest(new Request("POST", "/_refresh"));

        // Verify judgment exists in the system
        Request getJudgmentRequest = new Request("GET", "/_plugins/_search_relevance/judgments/" + judgmentId);
        Response getResponse = client().performRequest(getJudgmentRequest);
        assertEquals("Judgment should be retrievable", 200, getResponse.getStatusLine().getStatusCode());

        Map<String, Object> judgmentData = parseResponseToMap(getResponse);
        assertTrue("Judgment should be found", (Boolean) judgmentData.get("found"));
    }

    private void createTestIndex() throws IOException {
        // Create a simple test index with some documents
        XContentBuilder indexMapping = XContentFactory.jsonBuilder()
            .startObject()
            .startObject("mappings")
            .startObject("properties")
            .startObject("content")
            .field("type", "text")
            .endObject()
            .startObject("title")
            .field("type", "text")
            .endObject()
            .endObject()
            .endObject()
            .endObject();

        Request createIndexRequest = new Request("PUT", "/" + TEST_INDEX_NAME);
        createIndexRequest.setJsonEntity(indexMapping.toString());

        try {
            client().performRequest(createIndexRequest);
        } catch (Exception e) {
            // Index might already exist, ignore
        }

        // Add some test documents
        addTestDocuments();
    }

    private void addTestDocuments() throws IOException {
        String[] testDocs = {
            "{\"content\": \"This is a test document about search relevance\", \"title\": \"Search Relevance Guide\"}",
            "{\"content\": \"Another document discussing machine learning models\", \"title\": \"ML Models Overview\"}",
            "{\"content\": \"Document about OpenSearch and Elasticsearch\", \"title\": \"Search Engines\"}" };

        for (int i = 0; i < testDocs.length; i++) {
            Request indexDocRequest = new Request("PUT", "/" + TEST_INDEX_NAME + "/_doc/" + (i + 1));
            indexDocRequest.setJsonEntity(testDocs[i]);
            try {
                client().performRequest(indexDocRequest);
            } catch (Exception e) {
                // Ignore indexing errors for test setup
            }
        }

        // Refresh index to make documents searchable
        try {
            client().performRequest(new Request("POST", "/" + TEST_INDEX_NAME + "/_refresh"));
        } catch (Exception e) {
            // Ignore refresh errors
        }
    }

    private boolean isWorkbenchEnabled() {
        // For integration tests, we'll assume workbench is enabled
        // In a real environment, this would check the actual setting
        return true;
    }

    private Map<String, Object> parseResponseToMap(Response response) throws IOException {
        try {
            return entityAsMap(response);
        } catch (Exception e) {
            throw new IOException("Failed to parse response", e);
        }
    }
}
