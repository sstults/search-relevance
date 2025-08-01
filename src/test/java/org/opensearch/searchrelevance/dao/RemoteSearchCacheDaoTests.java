/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.search.TotalHits;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.common.PluginConstants;
import org.opensearch.searchrelevance.model.RemoteSearchCache;
import org.opensearch.transport.client.Client;

public class RemoteSearchCacheDaoTests extends org.apache.lucene.tests.util.LuceneTestCase {

    @Mock
    private Client client;

    private RemoteSearchCacheDao cacheDao;

    // @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        cacheDao = new RemoteSearchCacheDao(client);
    }

    public void testStoreCache() throws InterruptedException {
        // Create test cache entry
        RemoteSearchCache cache = new RemoteSearchCache(
            "test-cache-id",
            "config-1",
            "test-query-hash",
            "test query",
            "{\"response\": \"data\"}",
            "{\"mapped\": \"response\"}",
            Instant.now().toEpochMilli(),
            Instant.now().toEpochMilli() + (60L * 60 * 1000) // 60 minutes from now
        );

        // Mock successful index response
        IndexResponse mockResponse = mock(IndexResponse.class);
        when(mockResponse.getId()).thenReturn("test-cache-id");

        // Capture the index request
        ArgumentCaptor<IndexRequest> requestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        ArgumentCaptor<ActionListener<IndexResponse>> listenerCaptor = ArgumentCaptor.forClass(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).index(requestCaptor.capture(), listenerCaptor.capture());

        // Test store operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<IndexResponse> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.storeCache(cache, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());

        // Verify request details
        IndexRequest capturedRequest = requestCaptor.getValue();
        assertEquals(PluginConstants.REMOTE_SEARCH_CACHE_INDEX, capturedRequest.index());
        assertEquals("test-cache-id", capturedRequest.id());
    }

    public void testGetCacheHit() throws InterruptedException {
        String cacheKey = "test-cache-key";

        // Create test cache data
        Map<String, Object> sourceMap = Map.of(
            RemoteSearchCache.ID_FIELD,
            cacheKey,
            RemoteSearchCache.CONFIGURATION_ID_FIELD,
            "config-1",
            RemoteSearchCache.QUERY_HASH_FIELD,
            "query-hash",
            RemoteSearchCache.QUERY_TEXT_FIELD,
            "test query",
            RemoteSearchCache.RAW_RESPONSE_FIELD,
            "{\"response\": \"data\"}",
            RemoteSearchCache.MAPPED_RESPONSE_FIELD,
            "{\"mapped\": \"response\"}",
            RemoteSearchCache.TIMESTAMP_FIELD,
            Instant.now().toEpochMilli(),
            RemoteSearchCache.EXPIRATION_TIMESTAMP,
            Instant.now().toEpochMilli() + (60L * 60 * 1000)
        );

        // Mock successful get response
        GetResponse mockResponse = mock(GetResponse.class);
        when(mockResponse.isExists()).thenReturn(true);
        when(mockResponse.getSourceAsMap()).thenReturn(sourceMap);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any(ActionListener.class));

        // Test get operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchCache> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.getCache(cacheKey, new ActionListener<RemoteSearchCache>() {
            @Override
            public void onResponse(RemoteSearchCache cache) {
                result.set(cache);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());
        assertEquals(cacheKey, result.get().getId());
        assertEquals("config-1", result.get().getConfigurationId());
    }

    public void testGetCacheMiss() throws InterruptedException {
        String cacheKey = "non-existent-key";

        // Mock cache miss response
        GetResponse mockResponse = mock(GetResponse.class);
        when(mockResponse.isExists()).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any(ActionListener.class));

        // Test get operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchCache> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.getCache(cacheKey, new ActionListener<RemoteSearchCache>() {
            @Override
            public void onResponse(RemoteSearchCache cache) {
                result.set(cache);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNull(result.get()); // Should be null for cache miss
    }

    public void testGetExpiredCache() throws InterruptedException {
        String cacheKey = "expired-cache-key";

        // Create expired cache data (timestamp from 2 hours ago, expiration 1 hour ago)
        Instant expiredTime = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant expirationTime = Instant.now().minus(1, ChronoUnit.HOURS);
        Map<String, Object> sourceMap = Map.of(
            RemoteSearchCache.ID_FIELD,
            cacheKey,
            RemoteSearchCache.CONFIGURATION_ID_FIELD,
            "config-1",
            RemoteSearchCache.QUERY_HASH_FIELD,
            "query-hash",
            RemoteSearchCache.QUERY_TEXT_FIELD,
            "test query",
            RemoteSearchCache.RAW_RESPONSE_FIELD,
            "{\"response\": \"data\"}",
            RemoteSearchCache.MAPPED_RESPONSE_FIELD,
            "{\"mapped\": \"response\"}",
            RemoteSearchCache.TIMESTAMP_FIELD,
            expiredTime.toEpochMilli(),
            RemoteSearchCache.EXPIRATION_TIMESTAMP,
            expirationTime.toEpochMilli()
        );

        // Mock get response for expired cache
        GetResponse mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.isExists()).thenReturn(true);
        when(mockGetResponse.getSourceAsMap()).thenReturn(sourceMap);

        // Mock delete response for cleanup
        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockGetResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(client).delete(any(DeleteRequest.class), any(ActionListener.class));

        // Test get operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchCache> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.getCache(cacheKey, new ActionListener<RemoteSearchCache>() {
            @Override
            public void onResponse(RemoteSearchCache cache) {
                result.set(cache);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNull(result.get()); // Should be null for expired cache

        // Verify delete was called for cleanup
        verify(client, times(1)).delete(any(DeleteRequest.class), any(ActionListener.class));
    }

    public void testDeleteCache() throws InterruptedException {
        String cacheKey = "cache-to-delete";

        // Mock successful delete response
        DeleteResponse mockResponse = mock(DeleteResponse.class);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).delete(any(DeleteRequest.class), any(ActionListener.class));

        // Test delete operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DeleteResponse> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.deleteCache(cacheKey, new ActionListener<DeleteResponse>() {
            @Override
            public void onResponse(DeleteResponse response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());

        // Verify delete request
        ArgumentCaptor<DeleteRequest> requestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        verify(client).delete(requestCaptor.capture(), any(ActionListener.class));

        DeleteRequest capturedRequest = requestCaptor.getValue();
        assertEquals(PluginConstants.REMOTE_SEARCH_CACHE_INDEX, capturedRequest.index());
        assertEquals(cacheKey, capturedRequest.id());
    }

    public void testClearCacheForConfiguration() throws InterruptedException {
        String configurationId = "config-to-clear";

        // Create search response with cache entries
        SearchHit hit1 = new SearchHit(1, "cache-1", Map.of(), Map.of());
        SearchHit hit2 = new SearchHit(2, "cache-2", Map.of(), Map.of());
        SearchHits searchHits = new SearchHits(new SearchHit[] { hit1, hit2 }, new TotalHits(2, TotalHits.Relation.EQUAL_TO), 1.0f);

        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);

        // Mock delete responses
        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(client).delete(any(DeleteRequest.class), any(ActionListener.class));

        // Test clear operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.clearCacheForConfiguration(configurationId, new ActionListener<Void>() {
            @Override
            public void onResponse(Void response) {
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());

        // Verify search and delete calls
        verify(client, times(1)).search(any(SearchRequest.class), any(ActionListener.class));
        verify(client, times(2)).delete(any(DeleteRequest.class), any(ActionListener.class));
    }

    public void testGetCacheStats() throws InterruptedException {
        // Create search response with aggregations
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(100L, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);

        // Create proper aggregations mock - return null to avoid internal implementation issues
        when(mockResponse.getAggregations()).thenReturn(null);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        // Test stats operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.getCacheStats(new ActionListener<Map<String, Object>>() {
            @Override
            public void onResponse(Map<String, Object> stats) {
                result.set(stats);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());
        assertTrue(result.get().containsKey("total_entries"));
        assertTrue(result.get().containsKey("aggregations"));
    }

    private SearchHit createMockSearchHit(String id) {
        SearchHit hit = mock(SearchHit.class);
        when(hit.getId()).thenReturn(id);
        when(hit.getIndex()).thenReturn(PluginConstants.REMOTE_SEARCH_CACHE_INDEX);
        return hit;
    }
}
