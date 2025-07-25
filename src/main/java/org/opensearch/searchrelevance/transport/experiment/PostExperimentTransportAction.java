/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_QUERY_TEXT;
import static org.opensearch.searchrelevance.common.MetricsConstants.POINTWISE_FIELD_NAME_EVALUATION_ID;
import static org.opensearch.searchrelevance.common.MetricsConstants.POINTWISE_FIELD_NAME_SEARCH_CONFIGURATION_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.EvaluationResult;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Handles transport actions for importing experiments in the system.
 */
public class PostExperimentTransportAction extends HandledTransportAction<PostExperimentRequest, IndexResponse> {

    private final ClusterService clusterService;
    private final ExperimentDao experimentDao;
    private final EvaluationResultDao evaluationResultDao;
    private final ExperimentVariantDao experimentVariantDao;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final MetricsHelper metricsHelper;

    private static final Logger LOGGER = LogManager.getLogger(PostExperimentTransportAction.class);

    @Inject
    public PostExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao,
        ExperimentVariantDao experimentVariantDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        EvaluationResultDao evaluationResultDao,
        MetricsHelper metricsHelper
    ) {
        super(PostExperimentAction.NAME, transportService, actionFilters, PostExperimentRequest::new);
        this.clusterService = clusterService;
        this.experimentDao = experimentDao;
        this.experimentVariantDao = experimentVariantDao;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.evaluationResultDao = evaluationResultDao;
        this.metricsHelper = metricsHelper;
    }

    @Override
    protected void doExecute(Task task, PostExperimentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }

        try {
            // Validate input (synchronously)
            validateRequest(request);

            // Create experiment as COMPLETED (data already processed)
            String experimentId = UUID.randomUUID().toString();
            LOGGER.info("Creating experiment with ID: {}", experimentId);
            List<Map<String, Object>> results = new ArrayList<>();

            // Persist evaluation results (synchronously)
            for (Map<String, Object> evalData : request.getEvaluationResultList()) {
                EvaluationResult result = createEvaluationResult(evalData, request);
                evaluationResultDao.putEvaluationResultSync(result);
                results.add(createResultSummary(result));
            }

            // Create final experiment with COMPLETED status
            Experiment experiment = new Experiment(
                experimentId,
                TimeUtils.getTimestamp(),
                request.getType(),
                AsyncStatus.COMPLETED, // Already complete!
                request.getQuerySetId(),
                request.getSearchConfigurationList(),
                request.getJudgmentList(),
                request.getSize(),
                results
            );

            // Persist and return
            experimentDao.putExperiment(experiment, listener);

        } catch (Exception e) {
            LOGGER.error("Failed to import experiment", e);
            listener.onFailure(new SearchRelevanceException("Import failed", e, RestStatus.BAD_REQUEST));
        }
    }

    /**
     * Validates the request and returns the search configuration ID
     * @param request - the request to validate
     * @return search configuration ID
     * @throws Exception if validation fails
     */
    private String validateRequest(PostExperimentRequest request) throws Exception {
        List<SearchConfiguration> searchConfigurations = request.getSearchConfigurationList()
            .stream()
            .map(searchConfigurationDao::getSearchConfigurationSync)
            .toList();

        if (searchConfigurations.size() != 1) {
            throw new Exception("Must have exactly one search configuration. Had " + searchConfigurations.size() + " size.");
        }
        String searchConfigurationId = searchConfigurations.getFirst().id();

        if (request.getJudgmentList().size() != 1) {
            throw new Exception("Must have exactly one judgment list. Had " + request.getJudgmentList().size() + " size.");
        }
        return searchConfigurationId;
    }

    /**
     * Creates an EvaluationResult from evaluation data and request
     * @param evalData - evaluation data map
     * @param request - the original request
     * @return EvaluationResult object
     */
    private EvaluationResult createEvaluationResult(Map<String, Object> evalData, PostExperimentRequest request) {
        String evaluationId = UUID.randomUUID().toString();
        String searchConfigurationId = request.getSearchConfigurationList().get(0); // Already validated to have exactly one
        String queryText = (String) evalData.get("searchText");
        List<String> judgmentList = request.getJudgmentList();
        List<String> documentIds = (List<String>) evalData.get("documentIds");
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) evalData.get("metrics");

        return new EvaluationResult(
            evaluationId,
            TimeUtils.getTimestamp(),
            searchConfigurationId,
            queryText,
            judgmentList,
            documentIds,
            metrics
        );
    }

    /**
     * Creates a result summary map for the experiment results
     * @param result - the evaluation result
     * @return summary map
     */
    private Map<String, Object> createResultSummary(EvaluationResult result) {
        Map<String, Object> summary = new HashMap<>();
        summary.put(POINTWISE_FIELD_NAME_SEARCH_CONFIGURATION_ID, result.searchConfigurationId());
        summary.put(POINTWISE_FIELD_NAME_EVALUATION_ID, result.id());
        summary.put(PAIRWISE_FIELD_NAME_QUERY_TEXT, result.searchText());
        return summary;
    }

}
