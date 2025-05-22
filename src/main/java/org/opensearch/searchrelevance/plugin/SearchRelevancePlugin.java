/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.plugin;

import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.opensearch.action.ActionRequest;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.IngestPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
import org.opensearch.searchrelevance.rest.RestCreateQuerySetAction;
import org.opensearch.searchrelevance.transport.QuerySetAction;
import org.opensearch.searchrelevance.transport.QuerySetTransportAction;

public class SearchRelevancePlugin extends Plugin implements IngestPlugin, ActionPlugin {

    @Override
    public List<RestHandler> getRestHandlers(
        Settings settings,
        RestController restController,
        ClusterSettings clusterSettings,
        IndexScopedSettings indexScopedSettings,
        SettingsFilter settingsFilter,
        IndexNameExpressionResolver indexNameExpressionResolver,
        Supplier<DiscoveryNodes> nodesInCluster
    ) {
        RestCreateQuerySetAction createQuerySetAction = new RestCreateQuerySetAction();
        return singletonList(createQuerySetAction);
    };

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> querysetHandler = List.of(
            new ActionHandler<>(QuerySetAction.INSTANCE, QuerySetTransportAction.class)
        );
        List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> allHandlers = new ArrayList<>();
        allHandlers.addAll(querysetHandler);
        return allHandlers;
    }
}
