/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.searchrelevance.common.PluginConstants.DOCUMENT_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY_TEXT;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.model.SearchParams;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.experiment.GetExperimentAction;
import org.opensearch.searchrelevance.utils.ParserUtils;
import org.opensearch.transport.client.node.NodeClient;

public class RestGetExperimentAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestGetExperimentAction.class);
    private static final String GET_EXPERIMENT_ACTION = "get_experiment_action";

    @Override
    public String getName() {
        return GET_EXPERIMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(
            new Route(GET, String.format(Locale.ROOT, "%s/{%s}", EXPERIMENTS_URI, DOCUMENT_ID)),
            new Route(GET, EXPERIMENTS_URI)
        );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        final String experimentId = request.param(DOCUMENT_ID);
        // introduce query text as params to support eyeballing visualization
        final String queryText = request.param(QUERY_TEXT);
        // If id is provided, get specific query set
        if (experimentId != null && !experimentId.isEmpty()) {
            OpenSearchDocRequest getRequest = new OpenSearchDocRequest(experimentId);
            return executeGetRequest(client, getRequest);
        }

        // Otherwise, handle list request
        SearchParams searchParams = ParserUtils.parseSearchParams(request);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())
            .size(searchParams.getSize())
            .sort(searchParams.getSortField(), searchParams.getSortOrder());

        OpenSearchDocRequest getRequest = new OpenSearchDocRequest(searchSourceBuilder);
        return executeGetRequest(client, getRequest);
    }

    private RestChannelConsumer executeGetRequest(NodeClient client, OpenSearchDocRequest request) {
        return channel -> client.execute(GetExperimentAction.INSTANCE, request, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    response.toXContent(builder, ToXContent.EMPTY_PARAMS);
                    RestStatus status = response.status();
                    channel.sendResponse(new BytesRestResponse(status, builder));
                } catch (IOException e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    channel.sendResponse(new BytesRestResponse(channel, e));
                } catch (IOException ex) {
                    logger.error("Failed to send error response", ex);
                }
            }
        });
    }
}
