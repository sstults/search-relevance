/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.RemoteSearchCacheDao;
import org.opensearch.searchrelevance.dao.RemoteSearchConfigurationDao;
import org.opensearch.searchrelevance.dao.RemoteSearchFailureDao;
import org.opensearch.searchrelevance.model.RemoteSearchCache;
import org.opensearch.searchrelevance.model.RemoteSearchConfiguration;
import org.opensearch.searchrelevance.model.RemoteSearchFailure;
import org.opensearch.searchrelevance.utils.TimeUtils;

import lombok.extern.log4j.Log4j2;

/**
 * RemoteSearchExecutor handles HTTP requests to remote search engines with rate limiting,
 * caching, and comprehensive error handling. This enables experiments to run against
 * remote OpenSearch clusters or other search engines via HTTPS.
 */
@Log4j2
public class RemoteSearchExecutor {

    private final RemoteSearchConfigurationDao remoteSearchConfigurationDao;
    private final RemoteSearchCacheDao remoteSearchCacheDao;
    private final RemoteSearchFailureDao remoteSearchFailureDao;
    private final RemoteResponseMapper remoteResponseMapper;
    private final HttpClient httpClient;

    // Rate limiting: Map of config ID to semaphore for concurrent request limiting
    private final Map<String, Semaphore> concurrentRequestLimiters = new ConcurrentHashMap<>();

    // Rate limiting: Map of config ID to last request timestamp for requests per second limiting
    private final Map<String, Long> lastRequestTimestamps = new ConcurrentHashMap<>();

    /**
     * Constructor with all dependencies
     */
    public RemoteSearchExecutor(
        RemoteSearchConfigurationDao remoteSearchConfigurationDao,
        RemoteSearchCacheDao remoteSearchCacheDao,
        RemoteSearchFailureDao remoteSearchFailureDao,
        RemoteResponseMapper remoteResponseMapper
    ) {
        this.remoteSearchConfigurationDao = remoteSearchConfigurationDao;
        this.remoteSearchCacheDao = remoteSearchCacheDao;
        this.remoteSearchFailureDao = remoteSearchFailureDao;
        this.remoteResponseMapper = remoteResponseMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    /**
     * Constructor for testing that allows injection of custom HttpClient
     */
    public RemoteSearchExecutor(
        RemoteSearchConfigurationDao remoteSearchConfigurationDao,
        RemoteSearchCacheDao remoteSearchCacheDao,
        RemoteSearchFailureDao remoteSearchFailureDao,
        RemoteResponseMapper remoteResponseMapper,
        HttpClient httpClient
    ) {
        this.remoteSearchConfigurationDao = remoteSearchConfigurationDao;
        this.remoteSearchCacheDao = remoteSearchCacheDao;
        this.remoteSearchFailureDao = remoteSearchFailureDao;
        this.remoteResponseMapper = remoteResponseMapper;
        this.httpClient = httpClient;
    }

    /**
     * Execute a remote search request with rate limiting, caching, and error handling
     *
     * @param remoteConfigId The remote configuration ID
     * @param query The search query (JSON string)
     * @param queryText The original query text for caching
     * @param experimentId The experiment ID for failure tracking
     * @param listener ActionListener for async response handling
     */
    public void executeRemoteSearch(
        String remoteConfigId,
        String query,
        String queryText,
        String experimentId,
        ActionListener<RemoteSearchResponse> listener
    ) {
        // First, get the remote configuration
        remoteSearchConfigurationDao.getRemoteSearchConfiguration(remoteConfigId, ActionListener.wrap(config -> {
            if (config == null) {
                listener.onFailure(new IllegalArgumentException("Remote configuration not found: " + remoteConfigId));
                return;
            }

            // Check cache first
            String cacheKey = RemoteSearchCache.generateCacheKey(remoteConfigId, query, queryText);
            checkCacheAndExecute(config, query, queryText, experimentId, cacheKey, listener);
        }, error -> {
            log.error("Failed to retrieve remote configuration {}: {}", remoteConfigId, error.getMessage());
            listener.onFailure(error);
        }));
    }

    /**
     * Check cache for existing response, execute remote request if not cached
     */
    private void checkCacheAndExecute(
        RemoteSearchConfiguration config,
        String query,
        String queryText,
        String experimentId,
        String cacheKey,
        ActionListener<RemoteSearchResponse> listener
    ) {
        // Check cache first if caching is enabled
        if (config.getCacheTtlMinutes() > 0) {
            remoteSearchCacheDao.getCachedResponse(cacheKey, ActionListener.wrap(cachedResponse -> {
                if (cachedResponse != null && !cachedResponse.isExpired()) {
                    // Cache hit - return cached response
                    log.debug("Cache hit for config: {}, key: {}", config.getId(), cacheKey);

                    // Apply response mapping to cached response
                    String mappedResponse = applyResponseMapping(config, cachedResponse.getResponse());

                    RemoteSearchResponse response = new RemoteSearchResponse(
                        cachedResponse.getResponse(),
                        mappedResponse,
                        200, // Assume success for cached responses
                        true
                    );

                    listener.onResponse(response);
                } else {
                    // Cache miss or expired - execute remote request
                    log.debug("Cache miss for config: {}, key: {}", config.getId(), cacheKey);
                    executeRemoteRequest(config, query, queryText, experimentId, cacheKey, listener);
                }
            }, error -> {
                // Cache lookup failed - proceed with remote execution
                log.warn("Cache lookup failed for config: {}, proceeding with remote execution: {}", config.getId(), error.getMessage());
                executeRemoteRequest(config, query, queryText, experimentId, cacheKey, listener);
            }));
        } else {
            // Caching disabled - proceed directly to remote execution
            executeRemoteRequest(config, query, queryText, experimentId, cacheKey, listener);
        }
    }

    /**
     * Execute the actual remote HTTP request with rate limiting
     */
    private void executeRemoteRequest(
        RemoteSearchConfiguration config,
        String query,
        String queryText,
        String experimentId,
        String cacheKey,
        ActionListener<RemoteSearchResponse> listener
    ) {
        try {
            // Apply rate limiting
            if (!applyRateLimit(config)) {
                listener.onFailure(new RuntimeException("Rate limit exceeded for configuration: " + config.getId()));
                return;
            }

            // Process query template
            String processedQuery = processQueryTemplate(config.getQueryTemplate(), query, queryText);

            // Build HTTP request
            HttpRequest request = buildHttpRequest(config, processedQuery);

            // Execute request asynchronously
            CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            future.whenComplete((response, throwable) -> {
                releaseConcurrentRequestLimit(config.getId());

                if (throwable != null) {
                    handleRequestFailure(config, query, queryText, experimentId, throwable, listener);
                } else {
                    handleRequestSuccess(config, query, queryText, experimentId, cacheKey, response, listener);
                }
            });

        } catch (Exception e) {
            releaseConcurrentRequestLimit(config.getId());
            handleRequestFailure(config, query, queryText, experimentId, e, listener);
        }
    }

    /**
     * Apply rate limiting based on configuration settings
     */
    private boolean applyRateLimit(RemoteSearchConfiguration config) {
        String configId = config.getId();

        // Check concurrent request limit
        Semaphore concurrentLimiter = concurrentRequestLimiters.computeIfAbsent(
            configId,
            k -> new Semaphore(config.getMaxConcurrentRequests())
        );

        if (!concurrentLimiter.tryAcquire()) {
            log.warn("Concurrent request limit exceeded for config: {}", configId);
            return false;
        }

        // Check requests per second limit
        long currentTime = System.currentTimeMillis();
        Long lastRequestTime = lastRequestTimestamps.get(configId);

        if (lastRequestTime != null) {
            long timeSinceLastRequest = currentTime - lastRequestTime;
            long minIntervalMs = 1000 / config.getMaxRequestsPerSecond();

            if (timeSinceLastRequest < minIntervalMs) {
                concurrentLimiter.release(); // Release the concurrent permit
                log.warn("Requests per second limit exceeded for config: {}", configId);
                return false;
            }
        }

        lastRequestTimestamps.put(configId, currentTime);
        return true;
    }

    /**
     * Release concurrent request limit
     */
    private void releaseConcurrentRequestLimit(String configId) {
        Semaphore limiter = concurrentRequestLimiters.get(configId);
        if (limiter != null) {
            limiter.release();
        }
    }

    /**
     * Process query template by substituting placeholders
     */
    private String processQueryTemplate(String queryTemplate, String query, String queryText) {
        if (queryTemplate == null || queryTemplate.trim().isEmpty()) {
            return query; // Use query as-is if no template
        }

        // Replace common placeholders
        String processed = queryTemplate.replace("${query}", query)
            .replace("${queryText}", queryText)
            .replace("{{query}}", query)
            .replace("{{queryText}}", queryText);

        return processed;
    }

    /**
     * Build HTTP request with authentication and headers
     */
    private HttpRequest buildHttpRequest(RemoteSearchConfiguration config, String query) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(config.getConnectionUrl()))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(query, StandardCharsets.UTF_8));

        // Add basic authentication if credentials are provided
        if (config.getUsername() != null
            && !config.getUsername().trim().isEmpty()
            && config.getPassword() != null
            && !config.getPassword().trim().isEmpty()) {

            String credentials = config.getUsername() + ":" + config.getPassword();
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            requestBuilder.header("Authorization", "Basic " + encodedCredentials);
        }

        return requestBuilder.build();
    }

    /**
     * Handle successful HTTP response
     */
    private void handleRequestSuccess(
        RemoteSearchConfiguration config,
        String query,
        String queryText,
        String experimentId,
        String cacheKey,
        HttpResponse<String> response,
        ActionListener<RemoteSearchResponse> listener
    ) {
        try {
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String responseBody = response.body();

                // Apply response mapping
                String mappedResponse = applyResponseMapping(config, responseBody);

                // Cache the response if caching is enabled
                if (config.getCacheTtlMinutes() > 0) {
                    long currentTimestamp = System.currentTimeMillis();
                    long expirationTimestamp = currentTimestamp + (config.getCacheTtlMinutes() * 60 * 1000);

                    RemoteSearchCache cacheEntry = new RemoteSearchCache(
                        cacheKey,
                        config.getId(),
                        query,
                        queryText,
                        responseBody,
                        mappedResponse,
                        currentTimestamp,
                        expirationTimestamp
                    );

                    remoteSearchCacheDao.cacheResponse(
                        cacheEntry,
                        ActionListener.wrap(
                            success -> log.debug("Response cached for config: {}, key: {}", config.getId(), cacheKey),
                            error -> log.warn("Failed to cache response for config: {}: {}", config.getId(), error.getMessage())
                        )
                    );
                }

                RemoteSearchResponse remoteResponse = new RemoteSearchResponse(responseBody, mappedResponse, response.statusCode(), true);

                listener.onResponse(remoteResponse);

                log.debug("Remote search successful for config: {}, status: {}", config.getId(), response.statusCode());

            } else {
                // HTTP error status
                String errorMessage = String.format(Locale.ROOT, "HTTP %d: %s", response.statusCode(), response.body());
                Exception httpError = new IOException(errorMessage);
                handleRequestFailure(config, query, queryText, experimentId, httpError, listener);
            }

        } catch (Exception e) {
            handleRequestFailure(config, query, queryText, experimentId, e, listener);
        }
    }

    /**
     * Handle request failure with proper error categorization and logging
     */
    private void handleRequestFailure(
        RemoteSearchConfiguration config,
        String query,
        String queryText,
        String experimentId,
        Throwable error,
        ActionListener<RemoteSearchResponse> listener
    ) {
        log.error("Remote search failed for config: {}, error: {}", config.getId(), error.getMessage());

        // Create failure record for tracking
        String failureId = "failure_" + System.currentTimeMillis() + "_" + config.getId().hashCode();
        RemoteSearchFailure failure = RemoteSearchFailure.fromException(
            failureId,
            config.getId(),
            experimentId,
            query,
            queryText,
            error instanceof Exception ? (Exception) error : new RuntimeException(error),
            TimeUtils.getTimestamp()
        );

        // Store failure record for analysis and circuit breaker logic
        remoteSearchFailureDao.recordFailure(
            failure,
            ActionListener.wrap(
                success -> log.debug("Failure recorded for config: {}, failure ID: {}", config.getId(), failureId),
                storeError -> log.warn("Failed to store failure record for config: {}: {}", config.getId(), storeError.getMessage())
            )
        );

        // Return error response
        RemoteSearchResponse errorResponse = new RemoteSearchResponse(
            null,
            null,
            error instanceof IOException && error.getMessage().contains("HTTP") ? extractHttpStatusCode(error.getMessage()) : 0,
            false
        );

        listener.onFailure(new RuntimeException("Remote search failed: " + error.getMessage(), error));
    }

    /**
     * Apply response mapping using the RemoteResponseMapper
     */
    private String applyResponseMapping(RemoteSearchConfiguration config, String rawResponse) {
        try {
            if (config.getResponseTemplate() != null && !config.getResponseTemplate().trim().isEmpty()) {
                return remoteResponseMapper.mapResponse(rawResponse, config.getResponseTemplate());
            } else {
                // No response template - return raw response
                return rawResponse;
            }
        } catch (Exception e) {
            log.warn("Response mapping failed for config: {}, using raw response: {}", config.getId(), e.getMessage());
            return rawResponse;
        }
    }

    /**
     * Extract HTTP status code from error message
     */
    private int extractHttpStatusCode(String errorMessage) {
        try {
            if (errorMessage.startsWith("HTTP ")) {
                String statusPart = errorMessage.substring(5, errorMessage.indexOf(':'));
                return Integer.parseInt(statusPart);
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return 0;
    }

    /**
     * Response wrapper for remote search results
     */
    public static class RemoteSearchResponse {
        private final String rawResponse;
        private final String mappedResponse;
        private final int statusCode;
        private final boolean success;

        public RemoteSearchResponse(String rawResponse, String mappedResponse, int statusCode, boolean success) {
            this.rawResponse = rawResponse;
            this.mappedResponse = mappedResponse;
            this.statusCode = statusCode;
            this.success = success;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public String getMappedResponse() {
            return mappedResponse;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
