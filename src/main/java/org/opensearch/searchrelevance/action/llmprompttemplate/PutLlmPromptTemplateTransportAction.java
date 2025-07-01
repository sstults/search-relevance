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
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Transport action for putting LLM prompt templates
 */
public class PutLlmPromptTemplateTransportAction extends HandledTransportAction<PutLlmPromptTemplateRequest, PutLlmPromptTemplateResponse> {

    private final LlmPromptTemplateDao llmPromptTemplateDao;

    @Inject
    public PutLlmPromptTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        SearchRelevanceIndicesManager indicesManager
    ) {
        super(PutLlmPromptTemplateAction.NAME, transportService, actionFilters, PutLlmPromptTemplateRequest::new);
        this.llmPromptTemplateDao = new LlmPromptTemplateDao(indicesManager);
    }

    @Override
    protected void doExecute(Task task, PutLlmPromptTemplateRequest request, ActionListener<PutLlmPromptTemplateResponse> listener) {
        llmPromptTemplateDao.putLlmPromptTemplate(request.getTemplateId(), request.getTemplate(), ActionListener.wrap(indexResponse -> {
            PutLlmPromptTemplateResponse response = new PutLlmPromptTemplateResponse(
                indexResponse.getId(),
                indexResponse.getResult().toString()
            );
            listener.onResponse(response);
        }, listener::onFailure));
    }
}
