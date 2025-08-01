/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.common.PluginConstants;
import org.opensearch.searchrelevance.model.RemoteSearchCache;
import org.opensearch.transport.client.Client;

/**
 * Data Access Object for RemoteSearchCache operations.
 * Handles CRUD operations and TTL-based cache management.
 */
public class RemoteSearchCacheDao {
    private static final Logger logger = LogManager.getLogger(RemoteSearchCacheDao.class);

    private final Client client;

    public RemoteSearchCacheDao(Client client) {
        this.client = client;
    }

    /**
     * Store a cache entry with TTL-based expiration.
     *
     * @param cache the cache entry to store
     * @param listener callback for the operation result
     */
    public void storeCache(RemoteSearchCache cache, ActionListener<IndexResponse> listener) {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            cache.toXContent(builder, ToXContent.EMPTY_PARAMS);

            IndexRequest request = new IndexRequest(PluginConstants.REMOTE_SEARCH_CACHE_INDEX).id(cache.getId())
                .source(builder)
                .setRefreshPolicy(org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE);

            client.index(request, listener);
            logger.debug("Storing cache entry with ID: {}", cache.getId());
        } catch (IOException e) {
            logger.error("Failed to store cache entry: {}", e.getMessage(), e);
            listener.onFailure(e);
        }
    }

    /**
     * Retrieve a cache entry by cache key, checking TTL expiration.
     *
     * @param cacheKey the cache key to retrieve
     * @param listener callback with the cache entry or null if not found/expired
     */
    public void getCache(String cacheKey, ActionListener<RemoteSearchCache> listener) {
        GetRequest request = new GetRequest(PluginConstants.REMOTE_SEARCH_CACHE_INDEX, cacheKey);

        client.get(request, ActionListener.wrap(response -> {
            if (!response.isExists()) {
                logger.debug("Cache miss for key: {}", cacheKey);
                listener.onResponse(null);
                return;
            }

            try {
                RemoteSearchCache cache = RemoteSearchCache.fromSourceMap(response.getSourceAsMap());

                // Check if cache entry has expired
                if (cache.isExpired()) {
                    logger.debug("Cache entry expired for key: {}", cacheKey);
                    // Asynchronously delete expired entry
                    deleteCache(
                        cacheKey,
                        ActionListener.wrap(
                            deleteResponse -> logger.debug("Deleted expired cache entry: {}", cacheKey),
                            deleteError -> logger.warn("Failed to delete expired cache entry: {}", deleteError.getMessage())
                        )
                    );
                    listener.onResponse(null);
                    return;
                }

                logger.debug("Cache hit for key: {}", cacheKey);
                listener.onResponse(cache);
            } catch (Exception e) {
                logger.error("Failed to parse cache entry for key {}: {}", cacheKey, e.getMessage(), e);
                listener.onFailure(e);
            }
        }, error -> {
            logger.error("Failed to retrieve cache entry for key {}: {}", cacheKey, error.getMessage(), error);
            listener.onFailure(error);
        }));
    }

    /**
     * Delete a cache entry by cache key.
     *
     * @param cacheKey the cache key to delete
     * @param listener callback for the operation result
     */
    public void deleteCache(String cacheKey, ActionListener<DeleteResponse> listener) {
        DeleteRequest request = new DeleteRequest(PluginConstants.REMOTE_SEARCH_CACHE_INDEX, cacheKey).setRefreshPolicy(
            org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE
        );

        client.delete(request, ActionListener.wrap(response -> {
            logger.debug("Deleted cache entry with key: {}", cacheKey);
            listener.onResponse(response);
        }, error -> {
            logger.error("Failed to delete cache entry for key {}: {}", cacheKey, error.getMessage(), error);
            listener.onFailure(error);
        }));
    }

    /**
     * Delete all cache entries for a specific configuration.
     *
     * @param configurationId the configuration ID to clear cache for
     * @param listener callback for the operation result
     */
    public void clearCacheForConfiguration(String configurationId, ActionListener<Void> listener) {
        // First, search for all cache entries with the given configuration ID
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery(RemoteSearchCache.CONFIGURATION_ID_FIELD, configurationId));

        SearchRequest searchRequest = new SearchRequest(PluginConstants.REMOTE_SEARCH_CACHE_INDEX).source(
            new SearchSourceBuilder().query(queryBuilder)
                .size(1000) // Process in batches
                .fetchSource(false)
        ); // We only need document IDs

        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            List<String> cacheKeysToDelete = new ArrayList<>();
            searchResponse.getHits().forEach(hit -> cacheKeysToDelete.add(hit.getId()));

            if (cacheKeysToDelete.isEmpty()) {
                logger.debug("No cache entries found for configuration: {}", configurationId);
                listener.onResponse(null);
                return;
            }

            // Delete cache entries in parallel
            deleteCacheEntries(cacheKeysToDelete, 0, listener);
        }, error -> {
            logger.error("Failed to search cache entries for configuration {}: {}", configurationId, error.getMessage(), error);
            listener.onFailure(error);
        }));
    }

    /**
     * Clean up expired cache entries across all configurations.
     *
     * @param listener callback for the operation result
     */
    public void cleanupExpiredEntries(ActionListener<Integer> listener) {
        // Search for expired entries
        RangeQueryBuilder expiredQuery = QueryBuilders.rangeQuery(RemoteSearchCache.TIMESTAMP_FIELD).lt(Instant.now().toEpochMilli());

        SearchRequest searchRequest = new SearchRequest(PluginConstants.REMOTE_SEARCH_CACHE_INDEX).source(
            new SearchSourceBuilder().query(expiredQuery)
                .size(1000) // Process in batches
                .fetchSource(false)
        ); // We only need document IDs

        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            List<String> expiredKeys = new ArrayList<>();
            searchResponse.getHits().forEach(hit -> expiredKeys.add(hit.getId()));

            if (expiredKeys.isEmpty()) {
                logger.debug("No expired cache entries found");
                listener.onResponse(0);
                return;
            }

            logger.info("Found {} expired cache entries to clean up", expiredKeys.size());
            deleteCacheEntries(expiredKeys, 0, ActionListener.wrap(result -> listener.onResponse(expiredKeys.size()), listener::onFailure));
        }, error -> {
            logger.error("Failed to search for expired cache entries: {}", error.getMessage(), error);
            listener.onFailure(error);
        }));
    }

    /**
     * Recursively delete cache entries from a list.
     */
    private void deleteCacheEntries(List<String> cacheKeys, int index, ActionListener<Void> listener) {
        if (index >= cacheKeys.size()) {
            listener.onResponse(null);
            return;
        }

        String cacheKey = cacheKeys.get(index);
        deleteCache(cacheKey, ActionListener.wrap(deleteResponse -> {
            // Continue with next entry
            deleteCacheEntries(cacheKeys, index + 1, listener);
        }, error -> {
            logger.warn("Failed to delete cache entry {}: {}", cacheKey, error.getMessage());
            // Continue with next entry even if this one failed
            deleteCacheEntries(cacheKeys, index + 1, listener);
        }));
    }

    /**
     * Alias for getCache() for compatibility with RemoteSearchExecutor
     */
    public void getCachedResponse(String cacheKey, ActionListener<RemoteSearchCache> listener) {
        getCache(cacheKey, listener);
    }

    /**
     * Alias for storeCache() for compatibility with RemoteSearchExecutor
     */
    public void cacheResponse(RemoteSearchCache cache, ActionListener<IndexResponse> listener) {
        storeCache(cache, listener);
    }

    /**
     * Get cache statistics for monitoring.
     *
     * @param listener callback with cache statistics
     */
    public void getCacheStats(ActionListener<Map<String, Object>> listener) {
        SearchRequest searchRequest = new SearchRequest(PluginConstants.REMOTE_SEARCH_CACHE_INDEX).source(
            new SearchSourceBuilder().size(0) // We only want aggregations
                .aggregation(
                    org.opensearch.search.aggregations.AggregationBuilders.terms("by_configuration")
                        .field(RemoteSearchCache.CONFIGURATION_ID_FIELD + ".keyword")
                        .size(100)
                )
                .aggregation(
                    org.opensearch.search.aggregations.AggregationBuilders.dateHistogram("by_hour")
                        .field(RemoteSearchCache.TIMESTAMP_FIELD)
                        .calendarInterval(org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval.HOUR)
                        .minDocCount(1)
                )
        );

        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("total_entries", searchResponse.getHits().getTotalHits().value());

            // Handle null aggregations
            if (searchResponse.getAggregations() != null) {
                stats.put("aggregations", searchResponse.getAggregations().asMap());
            } else {
                stats.put("aggregations", new java.util.HashMap<>());
            }
            listener.onResponse(stats);
        }, error -> {
            logger.error("Failed to get cache statistics: {}", error.getMessage(), error);
            listener.onFailure(error);
        }));
    }
}
