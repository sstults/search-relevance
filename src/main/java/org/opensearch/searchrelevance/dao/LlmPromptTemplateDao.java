/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import java.io.IOException;

import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.LlmPromptTemplate;

/**
 * DAO for managing LLM prompt templates
 */
public class LlmPromptTemplateDao {

    private final SearchRelevanceIndicesManager indicesManager;

    public LlmPromptTemplateDao(SearchRelevanceIndicesManager indicesManager) {
        this.indicesManager = indicesManager;
    }

    /**
     * Store or update an LLM prompt template
     */
    public void putLlmPromptTemplate(String templateId, LlmPromptTemplate template, ActionListener<IndexResponse> listener) {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            template.toXContent(builder, null);
            indicesManager.updateDoc(templateId, builder, SearchRelevanceIndices.LLM_PROMPT_TEMPLATE, listener);
        } catch (IOException e) {
            listener.onFailure(e);
        }
    }

    /**
     * Retrieve an LLM prompt template by ID
     */
    public void getLlmPromptTemplate(String templateId, ActionListener<SearchResponse> listener) {
        indicesManager.getDocByDocId(templateId, SearchRelevanceIndices.LLM_PROMPT_TEMPLATE, listener);
    }

    /**
     * Delete an LLM prompt template by ID
     */
    public void deleteLlmPromptTemplate(String templateId, ActionListener<DeleteResponse> listener) {
        indicesManager.deleteDocByDocId(templateId, SearchRelevanceIndices.LLM_PROMPT_TEMPLATE, listener);
    }

    /**
     * Search for LLM prompt templates
     */
    public void searchLlmPromptTemplates(String query, int from, int size, ActionListener<SearchResponse> listener) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).from(from).size(size);
        indicesManager.listDocsBySearchRequest(sourceBuilder, SearchRelevanceIndices.LLM_PROMPT_TEMPLATE, listener);
    }
}
