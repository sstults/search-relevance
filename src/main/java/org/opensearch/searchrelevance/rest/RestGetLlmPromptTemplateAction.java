/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.opensearch.rest.RestRequest.Method.GET;

import java.io.IOException;
import java.util.List;

import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.searchrelevance.action.llmprompttemplate.GetLlmPromptTemplateAction;
import org.opensearch.searchrelevance.action.llmprompttemplate.GetLlmPromptTemplateRequest;
import org.opensearch.transport.client.node.NodeClient;

/**
 * REST action for getting LLM prompt templates
 */
public class RestGetLlmPromptTemplateAction extends BaseRestHandler {

    @Override
    public String getName() {
        return "get_llm_prompt_template_action";
    }

    @Override
    public List<Route> routes() {
        return List.of(
            new Route(GET, "/_plugins/_search_relevance/llm_prompt_templates/{id}"),
            new Route(GET, "/_plugins/_search_relevance/llm_prompt_templates/_search")
        );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String id = request.param("id");
        // For _search endpoint, id will be null
        GetLlmPromptTemplateRequest getRequest = new GetLlmPromptTemplateRequest(id);

        return channel -> client.execute(GetLlmPromptTemplateAction.INSTANCE, getRequest, new RestToXContentListener<>(channel));
    }
}
