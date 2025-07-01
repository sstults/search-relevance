/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.llmprompttemplate;

import java.util.Locale;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.LlmPromptTemplateDao;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Transport action for deleting LLM prompt templates
 */
public class DeleteLlmPromptTemplateTransportAction extends HandledTransportAction<
    DeleteLlmPromptTemplateRequest,
    DeleteLlmPromptTemplateResponse> {

    private final LlmPromptTemplateDao llmPromptTemplateDao;

    @Inject
    public DeleteLlmPromptTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        SearchRelevanceIndicesManager indicesManager
    ) {
        super(DeleteLlmPromptTemplateAction.NAME, transportService, actionFilters, DeleteLlmPromptTemplateRequest::new);
        this.llmPromptTemplateDao = new LlmPromptTemplateDao(indicesManager);
    }

    @Override
    protected void doExecute(Task task, DeleteLlmPromptTemplateRequest request, ActionListener<DeleteLlmPromptTemplateResponse> listener) {
        llmPromptTemplateDao.deleteLlmPromptTemplate(request.getTemplateId(), ActionListener.wrap(deleteResponse -> {
            DeleteLlmPromptTemplateResponse response = new DeleteLlmPromptTemplateResponse(
                deleteResponse.getId(),
                deleteResponse.getResult().toString().toLowerCase(Locale.ROOT),
                deleteResponse.getResult().toString().equals("DELETED")
            );
            listener.onResponse(response);
        }, listener::onFailure));
    }
}
