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
 * Action for putting LLM prompt templates
 */
public class PutLlmPromptTemplateAction extends ActionType<PutLlmPromptTemplateResponse> {

    public static final PutLlmPromptTemplateAction INSTANCE = new PutLlmPromptTemplateAction();
    public static final String NAME = "cluster:admin/opensearch/search_relevance/llm_prompt_template/put";

    private PutLlmPromptTemplateAction() {
        super(NAME, PutLlmPromptTemplateResponse::new);
    }
}
