/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.opensearch.rest.RestRequest.Method.DELETE;

import java.io.IOException;
import java.util.List;

import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.searchrelevance.action.llmprompttemplate.DeleteLlmPromptTemplateAction;
import org.opensearch.searchrelevance.action.llmprompttemplate.DeleteLlmPromptTemplateRequest;
import org.opensearch.transport.client.node.NodeClient;

/**
 * REST action for deleting LLM prompt templates
 */
public class RestDeleteLlmPromptTemplateAction extends BaseRestHandler {

    @Override
    public String getName() {
        return "delete_llm_prompt_template_action";
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(DELETE, "/_plugins/_search_relevance/llm_prompt_templates/{id}"));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String id = request.param("id");
        DeleteLlmPromptTemplateRequest deleteRequest = new DeleteLlmPromptTemplateRequest(id);

        return channel -> client.execute(DeleteLlmPromptTemplateAction.INSTANCE, deleteRequest, new RestToXContentListener<>(channel));
    }
}
