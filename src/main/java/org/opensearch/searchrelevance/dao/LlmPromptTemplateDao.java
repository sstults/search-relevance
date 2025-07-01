/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.searchrelevance.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.ActionListener;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentFactory;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.common.PluginConstants;
import org.opensearch.searchrelevance.model.LlmPromptTemplate;

import java.io.IOException;

/**
 * Data Access Object for LLM prompt templates
 */
public class LlmPromptTemplateDao {
    
    private static final Logger logger = LogManager.getLogger(LlmPromptTemplateDao.class);
    
    private final Client client;
    
    public LlmPromptTemplateDao(Client client) {
        this.client = client;
    }
    
    /**
     * Create or update an LLM prompt template
     */
    public void putLlmPromptTemplate(String templateId, LlmPromptTemplate template, ActionListener<IndexResponse> listener) {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            template.toXContent(builder, null);
            
            IndexRequest request = new IndexRequest(PluginConstants.LLM_PROMPT_TEMPLATE_INDEX)
                .id(templateId)
                .source(builder)
                .setRefreshPolicy("immediate");
            
            client.index(request, listener);
        } catch (IOException e) {
            logger.error("Failed to create LLM prompt template", e);
            listener.onFailure(e);
        }
    }
    
    /**
     * Get an LLM prompt template by ID
     */
    public void getLlmPromptTemplate(String templateId, ActionListener<GetResponse> listener) {
        GetRequest request = new GetRequest(PluginConstants.LLM_PROMPT_TEMPLATE_INDEX, templateId);
        client.get(request, listener);
    }
    
    /**
     * Delete an LLM prompt template by ID
     */
    public void deleteLlmPromptTemplate(String templateId, ActionListener<DeleteResponse> listener) {
        DeleteRequest request = new DeleteRequest(PluginConstants.LLM_PROMPT_TEMPLATE_INDEX, templateId)
            .setRefreshPolicy("immediate");
        client.delete(request, listener);
    }
    
    /**
     * Search for LLM prompt templates
     */
    public void searchLlmPromptTemplates(String query, int from, int size, ActionListener<SearchResponse> listener) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
            .from(from)
            .size(size);
        
        if (query != null && !query.isEmpty()) {
            searchSourceBuilder.query(QueryBuilders.multiMatchQuery(query, "name", "description"));
        } else {
            searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        }
        
        SearchRequest searchRequest = new SearchRequest(PluginConstants.LLM_PROMPT_TEMPLATE_INDEX)
            .source(searchSourceBuilder);
        
        client.search(searchRequest, listener);
    }
}
