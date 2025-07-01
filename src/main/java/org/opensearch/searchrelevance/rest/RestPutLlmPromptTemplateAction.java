/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.opensearch.rest.RestRequest.Method.PUT;

import java.io.IOException;
import java.util.List;

import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.searchrelevance.action.llmprompttemplate.PutLlmPromptTemplateAction;
import org.opensearch.searchrelevance.action.llmprompttemplate.PutLlmPromptTemplateRequest;
import org.opensearch.transport.client.node.NodeClient;

/**
 * REST action for putting LLM prompt templates
 */
public class RestPutLlmPromptTemplateAction extends BaseRestHandler {

    @Override
    public String getName() {
        return "put_llm_prompt_template_action";
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(PUT, "/_plugins/_search_relevance/llm_prompt_templates/{id}"));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String id = request.param("id");
        PutLlmPromptTemplateRequest putRequest = PutLlmPromptTemplateRequest.fromXContent(request.contentParser(), id);

        return channel -> client.execute(PutLlmPromptTemplateAction.INSTANCE, putRequest, new RestToXContentListener<>(channel));
    }
}
