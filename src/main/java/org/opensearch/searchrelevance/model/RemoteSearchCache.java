/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * RemoteSearchCache represents cached responses from remote search engines.
 * This enables performance optimization by avoiding repeated remote calls for the same queries.
 */
public class RemoteSearchCache implements ToXContentObject {
    public static final String CACHE_KEY = "cacheKey";
    public static final String REMOTE_CONFIG_ID = "remoteConfigId";
    public static final String QUERY = "query";
    public static final String QUERY_TEXT = "queryText";
    public static final String CACHED_RESPONSE = "cachedResponse";
    public static final String MAPPED_RESPONSE = "mappedResponse";
    public static final String CACHE_TIMESTAMP = "cacheTimestamp";
    public static final String EXPIRATION_TIMESTAMP = "expirationTimestamp";

    private final String cacheKey;
    private final String remoteConfigId;
    private final String query;
    private final String queryText;
    private final String cachedResponse;
    private final String mappedResponse;
    private final long cacheTimestamp;
    private final long expirationTimestamp;

    public RemoteSearchCache(
        String cacheKey,
        String remoteConfigId,
        String query,
        String queryText,
        String cachedResponse,
        String mappedResponse,
        long cacheTimestamp,
        long expirationTimestamp
    ) {
        this.cacheKey = cacheKey;
        this.remoteConfigId = remoteConfigId;
        this.query = query;
        this.queryText = queryText;
        this.cachedResponse = cachedResponse;
        this.mappedResponse = mappedResponse;
        this.cacheTimestamp = cacheTimestamp;
        this.expirationTimestamp = expirationTimestamp;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(CACHE_KEY, this.cacheKey != null ? this.cacheKey : "");
        xContentBuilder.field(REMOTE_CONFIG_ID, this.remoteConfigId != null ? this.remoteConfigId : "");
        xContentBuilder.field(QUERY, this.query != null ? this.query : "");
        xContentBuilder.field(QUERY_TEXT, this.queryText != null ? this.queryText : "");
        xContentBuilder.field(CACHED_RESPONSE, this.cachedResponse != null ? this.cachedResponse : "");
        xContentBuilder.field(MAPPED_RESPONSE, this.mappedResponse != null ? this.mappedResponse : "");
        xContentBuilder.field(CACHE_TIMESTAMP, this.cacheTimestamp);
        xContentBuilder.field(EXPIRATION_TIMESTAMP, this.expirationTimestamp);
        return xContentBuilder.endObject();
    }

    /**
     * Check if this cache entry has expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTimestamp;
    }

    /**
     * Generate cache key from configuration ID, query, and query text
     */
    public static String generateCacheKey(String remoteConfigId, String query, String queryText) {
        return String.valueOf((remoteConfigId + query + queryText).hashCode());
    }

    // Getters
    public String getCacheKey() {
        return cacheKey;
    }

    public String getRemoteConfigId() {
        return remoteConfigId;
    }

    public String getQuery() {
        return query;
    }

    public String getQueryText() {
        return queryText;
    }

    public String getCachedResponse() {
        return cachedResponse;
    }

    public String getMappedResponse() {
        return mappedResponse;
    }

    public long getCacheTimestamp() {
        return cacheTimestamp;
    }

    public long getExpirationTimestamp() {
        return expirationTimestamp;
    }
}
