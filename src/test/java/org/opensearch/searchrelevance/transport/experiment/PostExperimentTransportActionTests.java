/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.EvaluationResult;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class PostExperimentTransportActionTests extends OpenSearchTestCase {

    private PostExperimentTransportAction transportAction;
    private ClusterService clusterService;
    private ExperimentDao experimentDao;
    private EvaluationResultDao evaluationResultDao;
    private ExperimentVariantDao experimentVariantDao;
    private QuerySetDao querySetDao;
    private SearchConfigurationDao searchConfigurationDao;
    private MetricsHelper metricsHelper;
    private TransportService transportService;
    private ActionFilters actionFilters;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        clusterService = mock(ClusterService.class);
        experimentDao = mock(ExperimentDao.class);
        evaluationResultDao = mock(EvaluationResultDao.class);
        experimentVariantDao = mock(ExperimentVariantDao.class);
        querySetDao = mock(QuerySetDao.class);
        searchConfigurationDao = mock(SearchConfigurationDao.class);
        metricsHelper = mock(MetricsHelper.class);
        transportService = mock(TransportService.class);
        actionFilters = mock(ActionFilters.class);

        transportAction = new PostExperimentTransportAction(
            clusterService,
            transportService,
            actionFilters,
            experimentDao,
            experimentVariantDao,
            querySetDao,
            searchConfigurationDao,
            evaluationResultDao,
            metricsHelper
        );
    }

    public void testDoExecute_NullRequest() throws Exception {
        Task task = mock(Task.class);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        ActionListener<IndexResponse> listener = new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                fail("Should not succeed with null request");
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                exceptionRef.set(e);
                latch.countDown();
            }
        };

        transportAction.doExecute(task, null, listener);

        assertTrue("Listener should be called within timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Exception should be set", exceptionRef.get());
        assertTrue("Should be SearchRelevanceException", exceptionRef.get() instanceof SearchRelevanceException);
        assertEquals("Request cannot be null", exceptionRef.get().getMessage());
    }

    public void testDoExecute_Success() throws Exception {
        // Setup test data
        List<Map<String, Object>> evaluationResults = List.of(
            Map.of(
                "searchText",
                "test query",
                "metrics",
                List.of(Map.of("metric", "dcg@10", "value", 0.8)),
                "documentIds",
                List.of("d1", "d2")
            )
        );

        PostExperimentRequest request = new PostExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION,
            "querySetId",
            List.of("searchConfigId"),
            List.of("judgmentId"),
            10,
            evaluationResults
        );

        // Mock search configuration
        SearchConfiguration searchConfig = mock(SearchConfiguration.class);
        when(searchConfig.id()).thenReturn("searchConfigId");
        when(searchConfigurationDao.getSearchConfigurationSync("searchConfigId")).thenReturn(searchConfig);

        // Mock experiment dao to return success
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test-experiment-id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(experimentDao).putExperiment(any(Experiment.class), any(ActionListener.class));

        // Mock evaluation result dao synchronous method
        when(evaluationResultDao.putEvaluationResultSync(any(EvaluationResult.class))).thenReturn(mockIndexResponse);

        Task task = mock(Task.class);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<IndexResponse> responseRef = new AtomicReference<>();

        ActionListener<IndexResponse> listener = new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not fail: " + e.getMessage());
                latch.countDown();
            }
        };

        transportAction.doExecute(task, request, listener);

        assertTrue("Listener should be called within timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Response should be set", responseRef.get());
        assertEquals("test-experiment-id", responseRef.get().getId());

        // Verify that experiment was created with COMPLETED status (synchronously)
        verify(experimentDao).putExperiment(any(Experiment.class), any(ActionListener.class));
        verify(evaluationResultDao).putEvaluationResultSync(any(EvaluationResult.class));
    }

    public void testDoExecute_ExperimentDaoFailure() throws Exception {
        PostExperimentRequest request = new PostExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION,
            "querySetId",
            List.of("searchConfigId"),
            List.of("judgmentId"),
            10,
            List.of(Map.of("searchText", "test"))
        );

        // Mock experiment dao to fail
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Database error"));
            return null;
        }).when(experimentDao).putExperiment(any(Experiment.class), any(ActionListener.class));

        Task task = mock(Task.class);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        ActionListener<IndexResponse> listener = new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                fail("Should not succeed when experiment dao fails");
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                exceptionRef.set(e);
                latch.countDown();
            }
        };

        transportAction.doExecute(task, request, listener);

        assertTrue("Listener should be called within timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Exception should be set", exceptionRef.get());
        assertTrue("Should be SearchRelevanceException", exceptionRef.get() instanceof SearchRelevanceException);
    }

    public void testValidateRequest_MultipleSearchConfigurations() throws Exception {
        PostExperimentRequest request = new PostExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION,
            "querySetId",
            List.of("searchConfigId1", "searchConfigId2"), // Multiple configs - should fail
            List.of("judgmentId"),
            10,
            List.of(Map.of("searchText", "test"))
        );

        SearchConfiguration searchConfig1 = mock(SearchConfiguration.class);
        SearchConfiguration searchConfig2 = mock(SearchConfiguration.class);
        when(searchConfigurationDao.getSearchConfigurationSync("searchConfigId1")).thenReturn(searchConfig1);
        when(searchConfigurationDao.getSearchConfigurationSync("searchConfigId2")).thenReturn(searchConfig2);

        Task task = mock(Task.class);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        ActionListener<IndexResponse> listener = new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                fail("Should not succeed with multiple search configurations");
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                exceptionRef.set(e);
                latch.countDown();
            }
        };

        transportAction.doExecute(task, request, listener);

        assertTrue("Listener should be called within timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Exception should be set", exceptionRef.get());
        assertTrue("Should be SearchRelevanceException", exceptionRef.get() instanceof SearchRelevanceException);
        assertTrue("Should contain validation error", exceptionRef.get().getMessage().contains("Import failed"));
    }
}
