/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.llmprompttemplate;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.LlmPromptTemplateDao;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.LlmPromptTemplate;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Transport action for getting LLM prompt templates
 */
public class GetLlmPromptTemplateTransportAction extends HandledTransportAction<GetLlmPromptTemplateRequest, GetLlmPromptTemplateResponse> {

    private final LlmPromptTemplateDao llmPromptTemplateDao;

    @Inject
    public GetLlmPromptTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        SearchRelevanceIndicesManager indicesManager
    ) {
        super(GetLlmPromptTemplateAction.NAME, transportService, actionFilters, GetLlmPromptTemplateRequest::new);
        this.llmPromptTemplateDao = new LlmPromptTemplateDao(indicesManager);
    }

    @Override
    protected void doExecute(Task task, GetLlmPromptTemplateRequest request, ActionListener<GetLlmPromptTemplateResponse> listener) {
        if (request.getTemplateId() != null && !request.getTemplateId().trim().isEmpty()) {
            // Get specific template by ID
            llmPromptTemplateDao.getLlmPromptTemplate(request.getTemplateId(), ActionListener.wrap(searchResponse -> {
                if (searchResponse.getHits().getTotalHits().value() > 0) {
                    try {
                        LlmPromptTemplate template = LlmPromptTemplate.fromXContent(searchResponse.getHits().getAt(0).getSourceAsMap());
                        GetLlmPromptTemplateResponse response = new GetLlmPromptTemplateResponse(template, true);
                        listener.onResponse(response);
                    } catch (Exception e) {
                        listener.onFailure(e);
                    }
                } else {
                    GetLlmPromptTemplateResponse response = new GetLlmPromptTemplateResponse((LlmPromptTemplate) null, false);
                    listener.onResponse(response);
                }
            }, listener::onFailure));
        } else {
            // Search all templates
            llmPromptTemplateDao.searchLlmPromptTemplates("", 0, 100, ActionListener.wrap(searchResponse -> {
                GetLlmPromptTemplateResponse response = new GetLlmPromptTemplateResponse(searchResponse, true);
                listener.onResponse(response);
            }, listener::onFailure));
        }
    }
}
