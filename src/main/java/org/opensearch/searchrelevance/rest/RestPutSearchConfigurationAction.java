/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static java.util.Collections.singletonList;
import static org.opensearch.rest.RestRequest.Method.PUT;
import static org.opensearch.searchrelevance.common.PluginConstants.INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.NAME;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_PIPELINE;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationRequest;
import org.opensearch.searchrelevance.utils.TextValidationUtil;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * Rest Action to facilitate requests to create a search configuration.
 */
@AllArgsConstructor
public class RestPutSearchConfigurationAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestPutSearchConfigurationAction.class);
    private static final String PUT_SEARCH_CONFIGURATION_ACTION = "put_search_configuration_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return PUT_SEARCH_CONFIGURATION_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(PUT, SEARCH_CONFIGURATIONS_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        String name = (String) source.get(NAME);
        TextValidationUtil.ValidationResult nameValidation = TextValidationUtil.validateText(name);
        if (!nameValidation.isValid()) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "Invalid name: " + nameValidation.getErrorMessage())
            );
        }
        String index = (String) source.get(INDEX);
        String queryBody = (String) source.get(QUERY);
        String searchPipeline = (String) source.getOrDefault(SEARCH_PIPELINE, "");

        PutSearchConfigurationRequest createRequest = new PutSearchConfigurationRequest(name, index, queryBody, searchPipeline);

        return channel -> client.execute(PutSearchConfigurationAction.INSTANCE, createRequest, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("search_configuration_id", response.getId());
                    builder.field("search_configuration_result", response.getResult());
                    builder.endObject();
                    channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                } catch (IOException e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    channel.sendResponse(new BytesRestResponse(channel, RestStatus.INTERNAL_SERVER_ERROR, e));
                } catch (IOException ex) {
                    LOGGER.error("Failed to send error response", ex);
                }
            }
        });
    }
}
