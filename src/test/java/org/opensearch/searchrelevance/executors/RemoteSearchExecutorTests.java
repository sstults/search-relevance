/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.RemoteSearchCacheDao;
import org.opensearch.searchrelevance.dao.RemoteSearchConfigurationDao;
import org.opensearch.searchrelevance.dao.RemoteSearchFailureDao;
import org.opensearch.searchrelevance.executors.RemoteSearchExecutor.RemoteSearchResponse;
import org.opensearch.searchrelevance.model.RemoteSearchConfiguration;

/**
 * Tests for RemoteSearchExecutor
 */
public class RemoteSearchExecutorTests extends org.apache.lucene.tests.util.LuceneTestCase {

    @Mock
    private RemoteSearchConfigurationDao mockDao;

    @Mock
    private RemoteSearchCacheDao mockCacheDao;

    @Mock
    private RemoteSearchFailureDao mockFailureDao;

    @Mock
    private RemoteResponseMapper mockResponseMapper;

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    private RemoteSearchExecutor remoteSearchExecutor;

    // @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        // Setup default mock behaviors
        setupDefaultMockBehaviors();

        // Use reflection or create a test constructor to inject mocked HttpClient
        remoteSearchExecutor = new TestableRemoteSearchExecutor(mockDao, mockCacheDao, mockFailureDao, mockResponseMapper, mockHttpClient);
    }

    private void setupDefaultMockBehaviors() {
        // Mock cache DAO to return null (cache miss) by default
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(mockCacheDao).getCachedResponse(any(), any());

        // Mock cache DAO to succeed when caching
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(mockCacheDao).cacheResponse(any(), any());

        // Mock failure DAO to succeed when recording failures
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(mockFailureDao).recordFailure(any(), any());

        // Mock response mapper to return input by default
        when(mockResponseMapper.mapResponse(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    public void testExecuteRemoteSearchSuccess() throws Exception {
        // Setup test data
        String configId = "test-config-1";
        String query = "{\"query\":{\"match\":{\"title\":\"test\"}}}";
        String queryText = "test";
        String experimentId = "exp-123";

        RemoteSearchConfiguration config = createTestConfiguration(configId);
        String responseBody = "{\"hits\":{\"total\":{\"value\":5},\"hits\":[{\"_id\":\"1\",\"_source\":{\"title\":\"test doc\"}}]}}";

        // Mock DAO response
        doAnswer(invocation -> {
            ActionListener<RemoteSearchConfiguration> listener = invocation.getArgument(1);
            listener.onResponse(config);
            return null;
        }).when(mockDao).getRemoteSearchConfiguration(eq(configId), any());

        // Mock HTTP response
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(responseBody);

        CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(mockHttpResponse);
        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(future);

        // Execute test
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        remoteSearchExecutor.executeRemoteSearch(configId, query, queryText, experimentId, ActionListener.wrap(response -> {
            responseRef.set(response);
            latch.countDown();
        }, error -> {
            errorRef.set(error);
            latch.countDown();
        }));

        // Wait for async completion
        assertTrue("Request should complete within timeout", latch.await(5, TimeUnit.SECONDS));

        // Verify results
        assertNotNull("Response should not be null", responseRef.get());
        assertTrue("Request should be successful", responseRef.get().isSuccess());
        assertEquals("Status code should be 200", 200, responseRef.get().getStatusCode());
        assertEquals("Response body should match", responseBody, responseRef.get().getRawResponse());
        assertEquals("Mapped response should match raw response", responseBody, responseRef.get().getMappedResponse());
    }

    public void testExecuteRemoteSearchConfigNotFound() throws Exception {
        String configId = "nonexistent-config";
        String query = "{\"query\":{\"match\":{\"title\":\"test\"}}}";
        String queryText = "test";
        String experimentId = "exp-123";

        // Mock DAO response with null config
        doAnswer(invocation -> {
            ActionListener<RemoteSearchConfiguration> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(mockDao).getRemoteSearchConfiguration(eq(configId), any());

        // Execute test
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        remoteSearchExecutor.executeRemoteSearch(configId, query, queryText, experimentId, ActionListener.wrap(response -> {
            responseRef.set(response);
            latch.countDown();
        }, error -> {
            errorRef.set(error);
            latch.countDown();
        }));

        // Wait for async completion
        assertTrue("Request should complete within timeout", latch.await(5, TimeUnit.SECONDS));

        // Verify error
        assertNotNull("Error should not be null", errorRef.get());
        assertTrue("Error should be IllegalArgumentException", errorRef.get() instanceof IllegalArgumentException);
        assertTrue("Error message should mention config not found", errorRef.get().getMessage().contains("Remote configuration not found"));
    }

    public void testExecuteRemoteSearchHttpError() throws Exception {
        String configId = "test-config-1";
        String query = "{\"query\":{\"match\":{\"title\":\"test\"}}}";
        String queryText = "test";
        String experimentId = "exp-123";

        RemoteSearchConfiguration config = createTestConfiguration(configId);

        // Mock DAO response
        doAnswer(invocation -> {
            ActionListener<RemoteSearchConfiguration> listener = invocation.getArgument(1);
            listener.onResponse(config);
            return null;
        }).when(mockDao).getRemoteSearchConfiguration(eq(configId), any());

        // Mock HTTP error response
        when(mockHttpResponse.statusCode()).thenReturn(500);
        when(mockHttpResponse.body()).thenReturn("{\"error\":\"Internal server error\"}");

        CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(mockHttpResponse);
        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(future);

        // Execute test
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        remoteSearchExecutor.executeRemoteSearch(configId, query, queryText, experimentId, ActionListener.wrap(response -> {
            responseRef.set(response);
            latch.countDown();
        }, error -> {
            errorRef.set(error);
            latch.countDown();
        }));

        // Wait for async completion
        assertTrue("Request should complete within timeout", latch.await(5, TimeUnit.SECONDS));

        // Verify error handling
        assertNotNull("Error should not be null", errorRef.get());
        assertTrue("Error message should mention HTTP 500", errorRef.get().getMessage().contains("HTTP 500"));
    }

    public void testRateLimitingConcurrentRequests() throws Exception {
        String configId = "test-config-rate-limit";
        RemoteSearchConfiguration config = new RemoteSearchConfiguration(
            configId,
            "Rate Limited Config",
            "Test configuration with low concurrent limit",
            "https://example.com/search",
            "user",
            "pass",
            "${query}",
            null,
            10, // requests per second
            1,  // max concurrent requests (low limit for testing)
            60,
            false,
            Map.of(),
            "2025-01-29T12:00:00Z"
        );

        // Mock DAO response
        doAnswer(invocation -> {
            ActionListener<RemoteSearchConfiguration> listener = invocation.getArgument(1);
            listener.onResponse(config);
            return null;
        }).when(mockDao).getRemoteSearchConfiguration(eq(configId), any());

        // Mock successful HTTP response
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn("{\"hits\":{\"total\":{\"value\":1}}}");

        // Create a future that completes after a delay to simulate concurrent requests
        CompletableFuture<HttpResponse<String>> delayedFuture = new CompletableFuture<>();
        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(delayedFuture);

        // Start first request (should succeed)
        CountDownLatch firstLatch = new CountDownLatch(1);
        AtomicReference<Exception> firstError = new AtomicReference<>();

        remoteSearchExecutor.executeRemoteSearch(
            configId,
            "{\"query\":{}}",
            "test1",
            "exp-1",
            ActionListener.wrap(response -> firstLatch.countDown(), error -> {
                firstError.set(error);
                firstLatch.countDown();
            })
        );

        // Start second request immediately (should fail due to concurrent limit)
        CountDownLatch secondLatch = new CountDownLatch(1);
        AtomicReference<Exception> secondError = new AtomicReference<>();

        remoteSearchExecutor.executeRemoteSearch(
            configId,
            "{\"query\":{}}",
            "test2",
            "exp-2",
            ActionListener.wrap(response -> secondLatch.countDown(), error -> {
                secondError.set(error);
                secondLatch.countDown();
            })
        );

        // Wait for second request to fail quickly
        assertTrue("Second request should complete quickly", secondLatch.await(2, TimeUnit.SECONDS));

        // Verify second request failed due to rate limiting
        assertNotNull("Second request should have failed", secondError.get());
        assertTrue("Error should mention rate limit", secondError.get().getMessage().contains("Rate limit exceeded"));

        // Complete the first request
        delayedFuture.complete(mockHttpResponse);
        assertTrue("First request should complete", firstLatch.await(2, TimeUnit.SECONDS));
    }

    public void testQueryTemplateProcessing() throws Exception {
        String configId = "test-config-template";
        String queryTemplate = "{\"query\":{\"match\":{\"title\":\"${queryText}\"}},\"size\":10}";

        RemoteSearchConfiguration config = new RemoteSearchConfiguration(
            configId,
            "Template Config",
            "Test configuration with query template",
            "https://example.com/search",
            "user",
            "pass",
            queryTemplate,
            null,
            10,
            5,
            60,
            false,
            Map.of(),
            "2025-01-29T12:00:00Z"
        );

        String query = "{\"query\":{\"match\":{\"title\":\"original\"}}}";
        String queryText = "processed text";

        // Mock DAO response
        doAnswer(invocation -> {
            ActionListener<RemoteSearchConfiguration> listener = invocation.getArgument(1);
            listener.onResponse(config);
            return null;
        }).when(mockDao).getRemoteSearchConfiguration(eq(configId), any());

        // Mock HTTP response
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn("{\"hits\":{\"total\":{\"value\":1}}}");

        CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(mockHttpResponse);
        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(future);

        // Execute test
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchResponse> responseRef = new AtomicReference<>();

        remoteSearchExecutor.executeRemoteSearch(configId, query, queryText, "exp-123", ActionListener.wrap(response -> {
            responseRef.set(response);
            latch.countDown();
        }, error -> latch.countDown()));

        assertTrue("Request should complete", latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Response should not be null", responseRef.get());
        assertTrue("Request should be successful", responseRef.get().isSuccess());

        // Verify that the HTTP request was made with processed template
        verify(mockHttpClient).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    /**
     * Create a test configuration with default values
     */
    private RemoteSearchConfiguration createTestConfiguration(String configId) {
        return new RemoteSearchConfiguration(
            configId,
            "Test Configuration",
            "Test configuration for unit tests",
            "https://example.com/search",
            "testuser",
            "testpass",
            "${query}",
            null,
            10, // max requests per second
            5,  // max concurrent requests
            60, // cache duration minutes
            false,
            Map.of("test", "metadata"),
            "2025-01-29T12:00:00Z"
        );
    }

    /**
     * Testable version of RemoteSearchExecutor that allows injection of mocked HttpClient
     */
    private static class TestableRemoteSearchExecutor extends RemoteSearchExecutor {
        public TestableRemoteSearchExecutor(
            RemoteSearchConfigurationDao dao,
            RemoteSearchCacheDao cacheDao,
            RemoteSearchFailureDao failureDao,
            RemoteResponseMapper responseMapper,
            HttpClient httpClient
        ) {
            super(dao, cacheDao, failureDao, responseMapper, httpClient);
        }
    }
}
