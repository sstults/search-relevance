/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.llmprompttemplate;

import org.opensearch.action.ActionType;

/**
 * Action for deleting LLM prompt templates
 */
public class DeleteLlmPromptTemplateAction extends ActionType<DeleteLlmPromptTemplateResponse> {

    public static final DeleteLlmPromptTemplateAction INSTANCE = new DeleteLlmPromptTemplateAction();
    public static final String NAME = "cluster:admin/opensearch/search_relevance/llm_prompt_template/delete";

    private DeleteLlmPromptTemplateAction() {
        super(NAME, DeleteLlmPromptTemplateResponse::new);
    }
}
