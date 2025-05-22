/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import static org.opensearch.searchrelevance.common.MLConstants.sanitizeLLMResponse;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_INDEX_AND_QUERIES_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_QUERY_TEXT_FIELD_NAME;
import static org.opensearch.searchrelevance.model.JudgmentCache.CONTEXT_FIELDS_STR;
import static org.opensearch.searchrelevance.model.JudgmentCache.SCORE;
import static org.opensearch.searchrelevance.model.QueryWithReference.DELIMITER;
import static org.opensearch.searchrelevance.model.builder.SearchRequestBuilder.buildSearchRequest;
import static org.opensearch.searchrelevance.utils.ParserUtils.combinedIndexAndDocId;
import static org.opensearch.searchrelevance.utils.ParserUtils.getDocIdFromCompositeKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.searchrelevance.dao.JudgmentCacheDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.ml.ChunkException;
import org.opensearch.searchrelevance.ml.ChunkResult;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.JudgmentCache;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.shared.StashedThreadContext;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.transport.client.Client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LlmJudgmentsProcessor implements BaseJudgmentsProcessor {
    private static final Logger LOGGER = LogManager.getLogger(LlmJudgmentsProcessor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MLAccessor mlAccessor;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final JudgmentCacheDao judgmentCacheDao;
    private final Client client;

    @Inject
    public LlmJudgmentsProcessor(
        MLAccessor mlAccessor,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        JudgmentCacheDao judgmentCacheDao,
        Client client
    ) {
        this.mlAccessor = mlAccessor;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.judgmentCacheDao = judgmentCacheDao;
        this.client = client;
    }

    @Override
    public JudgmentType getJudgmentType() {
        return JudgmentType.LLM_JUDGMENT;
    }

    @Override
    public void generateJudgmentScore(Map<String, Object> metadata, ActionListener<Map<String, Map<String, String>>> listener) {
        String querySetId = (String) metadata.get("querySetId");
        List<String> searchConfigurationList = (List<String>) metadata.get("searchConfigurationList");
        int size = (int) metadata.get("size");

        String modelId = (String) metadata.get("modelId");
        int tokenLimit = (int) metadata.get("tokenLimit");
        List<String> contextFields = (List<String>) metadata.get("contextFields");

        Map<String, Object> results = new HashMap<>();

        // Step 1: Get QuerySet
        StepListener<Map<String, Object>> getQuerySetStep = new StepListener<>();
        querySetDao.getQuerySetWithStepListener(querySetId, results, getQuerySetStep);

        // Step 2: Get Search Configurations
        StepListener<Map<String, Object>> getSearchConfigsStep = new StepListener<>();
        getQuerySetStep.whenComplete(querySetResults -> {
            searchConfigurationDao.getSearchConfigsWithStepListener(searchConfigurationList, results, getSearchConfigsStep);
        }, error -> {
            LOGGER.error("Failed to get query set", error);
            listener.onFailure(new SearchRelevanceException("Failed to get query set", error, RestStatus.INTERNAL_SERVER_ERROR));
        });

        // Step 3: Generate LLM Judgments
        getSearchConfigsStep.whenComplete(searchConfigResults -> {
            generateLLMJudgments(modelId, size, tokenLimit, contextFields, results, listener);
        }, error -> {
            LOGGER.error("Failed to get search configurations", error);
            listener.onFailure(
                new SearchRelevanceException("Failed to get search configurations", error, RestStatus.INTERNAL_SERVER_ERROR)
            );
        });
    }

    private void generateLLMJudgments(
        String modelId,
        int size,
        int tokenLimit,
        List<String> contextFields,
        Map<String, Object> results,
        ActionListener<Map<String, Map<String, String>>> listener
    ) {
        Map<String, List<String>> indexAndQueries = (Map<String, List<String>>) results.get(METRICS_INDEX_AND_QUERIES_FIELD_NAME);
        List<String> queryTextWithReferences = (List<String>) results.get(METRICS_QUERY_TEXT_FIELD_NAME);

        Map<String, Map<String, String>> allJudgments = new HashMap<>();
        AtomicInteger remainingQueries = new AtomicInteger(queryTextWithReferences.size());

        for (String queryTextWithReference : queryTextWithReferences) {
            processQueryText(
                modelId,
                size,
                tokenLimit,
                contextFields,
                indexAndQueries,
                queryTextWithReference,
                new ActionListener<Map<String, String>>() {
                    @Override
                    public void onResponse(Map<String, String> docIdToScore) {
                        synchronized (allJudgments) {
                            allJudgments.put(queryTextWithReference, docIdToScore);
                        }
                        if (remainingQueries.decrementAndGet() == 0) {
                            listener.onResponse(allJudgments);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        listener.onFailure(
                            new SearchRelevanceException("Failed to generate LLM judgments", e, RestStatus.INTERNAL_SERVER_ERROR)
                        );
                    }
                }
            );
        }
    }

    private void processQueryText(
        String modelId,
        int size,
        int tokenLimit,
        List<String> contextFields,
        Map<String, List<String>> indexAndQueries,
        String queryTextWithReference,
        ActionListener<Map<String, String>> listener
    ) {
        Map<String, String> unionHits = new HashMap<>();
        ConcurrentMap<String, String> docIdToScore = new ConcurrentHashMap<>();

        AtomicInteger pendingSearches = new AtomicInteger(indexAndQueries.size());
        for (Map.Entry<String, List<String>> entry : indexAndQueries.entrySet()) {
            String index = entry.getValue().get(0);
            String queryText = queryTextWithReference.split(DELIMITER, 2)[0];
            String query = entry.getValue().get(1);
            String searchPipeline = entry.getValue().get(2);

            SearchRequest searchRequest = buildSearchRequest(index, query, queryText, searchPipeline, size);
            StashedThreadContext.run(client, () -> client.search(searchRequest, ActionListener.wrap(response -> {
                SearchHit[] hits = response.getHits().getHits();
                List<String> docIds = Arrays.stream(hits).map(SearchHit::getId).collect(Collectors.toList());

                deduplicateFromProcessedDocs(
                    index,
                    queryTextWithReference,
                    docIds,
                    contextFields,
                    docIdToScore,
                    ActionListener.wrap(unprocessedDocIds -> {
                        Arrays.stream(hits).filter(hit -> unprocessedDocIds.contains(hit.getId())).forEach(hit -> {
                            String compositeKey = combinedIndexAndDocId(hit.getIndex(), hit.getId());
                            String contextSource;
                            if (contextFields != null && !contextFields.isEmpty()) {
                                Map<String, Object> filteredSource = new HashMap<>();
                                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                                for (String field : contextFields) {
                                    if (sourceAsMap.containsKey(field)) {
                                        filteredSource.put(field, sourceAsMap.get(field));
                                    }
                                }
                                try {
                                    contextSource = new ObjectMapper().writeValueAsString(filteredSource);
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                contextSource = hit.getSourceAsString();
                            }
                            unionHits.put(compositeKey, contextSource);
                        });

                        if (pendingSearches.decrementAndGet() == 0) {
                            generateLLMJudgmentForQueryText(
                                modelId,
                                queryTextWithReference,
                                tokenLimit,
                                contextFields,
                                unionHits,
                                docIdToScore,
                                listener
                            );
                        }
                    }, e -> {
                        LOGGER.error("Deduplication failed for index: {}", index, e);
                        if (pendingSearches.decrementAndGet() == 0) {
                            generateLLMJudgmentForQueryText(
                                modelId,
                                queryTextWithReference,
                                tokenLimit,
                                contextFields,
                                unionHits,
                                docIdToScore,
                                listener
                            );
                        }
                    })
                );
            }, e -> {
                LOGGER.error("Search failed for index: {}", index, e);
                if (pendingSearches.decrementAndGet() == 0) {
                    generateLLMJudgmentForQueryText(
                        modelId,
                        queryTextWithReference,
                        tokenLimit,
                        contextFields,
                        unionHits,
                        docIdToScore,
                        listener
                    );
                }
            })));
        }
    }

    /**
     * Generate LLM judgment for each queryText.
     * @param modelId - modelId to be used for the judgment generation
     * @param queryTextWithReference - queryText with its referenceAnswer
     * @param tokenLimit - llm model token limit
     * @param contextFields - filters on specific context fields
     * @param unprocessedUnionHits - hits pending judged
     * @param docIdToScore - map to store the judgment scores
     * @param listener - listen each chunk results and update judgment cache at earliest
     */
    private void generateLLMJudgmentForQueryText(
        String modelId,
        String queryTextWithReference,
        int tokenLimit,
        List<String> contextFields,
        Map<String, String> unprocessedUnionHits,
        Map<String, String> docIdToScore,
        ActionListener<Map<String, String>> listener
    ) {
        LOGGER.debug("calculating LLM evaluation with modelId: {} and unprocessed unionHits: {}", modelId, unprocessedUnionHits);
        LOGGER.debug("processed docIdToScore before llm evaluation: {}", docIdToScore);

        // If there are no unprocessed hits, return the cached results immediately
        if (unprocessedUnionHits.isEmpty()) {
            LOGGER.info("All hits found in cache, returning cached results for query: {}", queryTextWithReference);
            listener.onResponse(docIdToScore);
            return;
        }

        String[] queryTextRefArr = queryTextWithReference.split(DELIMITER);
        String queryText = queryTextRefArr[0];
        String referenceAnswer = queryTextRefArr.length > 1 ? queryTextWithReference.split(DELIMITER, 2)[1] : null;

        ConcurrentMap<String, String> processedScores = new ConcurrentHashMap<>(docIdToScore);

        mlAccessor.predict(modelId, tokenLimit, queryText, referenceAnswer, unprocessedUnionHits, new ActionListener<ChunkResult>() {
            @Override
            public void onResponse(ChunkResult chunkResult) {
                try {
                    String sanitizedResponse = sanitizeLLMResponse("[" + chunkResult.getResponse() + "]");
                    List<Map<String, Object>> scores = OBJECT_MAPPER.readValue(
                        sanitizedResponse,
                        new TypeReference<List<Map<String, Object>>>() {
                        }
                    );

                    // process score and update judgment cache
                    for (Map<String, Object> score : scores) {
                        String compositeKey = (String) score.get("id");
                        Double ratingScore = ((Number) score.get("rating_score")).doubleValue();
                        String docId = getDocIdFromCompositeKey(compositeKey);
                        processedScores.put(docId, ratingScore.toString());
                        updateJudgmentCache(compositeKey, queryTextWithReference, contextFields, ratingScore.toString(), modelId);
                    }

                    // If this was the last chunk, send final response
                    if (chunkResult.isLastChunk()) {
                        listener.onResponse(processedScores);
                    }
                } catch (Exception e) {
                    listener.onFailure(
                        new SearchRelevanceException("Failed to process chunk response", e, RestStatus.INTERNAL_SERVER_ERROR)
                    );
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (e instanceof ChunkException) {
                    ChunkException chunkError = (ChunkException) e;
                    LOGGER.error("Chunk {}/{} failed: {}", chunkError.getChunkIndex(), chunkError.getTotalChunks(), e.getMessage());
                }
                listener.onFailure(new SearchRelevanceException("Failed to process chunk", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        });
    }

    /**
     * Filter out processed queryText+docId+contextFields tuple from judgment
     * @param targetIndex - index to be searched
     * @param queryTextWithReference - queryTextWithReference to be deduplicated
     * @param contextFields - contextFields to be deduplicated
     * @param docIds - overall docIds from search
     * @param docIdToScore - add processed docIds and scores to global docIdToScore map
     */
    private void deduplicateFromProcessedDocs(
        String targetIndex,
        String queryTextWithReference,
        List<String> docIds,
        List<String> contextFields,
        ConcurrentMap<String, String> docIdToScore,
        ActionListener<List<String>> listener
    ) {
        AtomicInteger pendingChecks = new AtomicInteger(docIds.size());
        Set<String> unprocessedDocIds = Collections.synchronizedSet(new HashSet<>(docIds));

        for (String docId : docIds) {
            String compositeKey = combinedIndexAndDocId(targetIndex, docId);
            judgmentCacheDao.getJudgmentCache(queryTextWithReference, compositeKey, contextFields, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    if (response.getHits().getTotalHits().value() > 0) {
                        SearchHit hit = response.getHits().getHits()[0];
                        Map<String, Object> source = hit.getSourceAsMap();
                        String score = (String) source.get(SCORE);
                        String storedContextFields = (String) source.get(CONTEXT_FIELDS_STR);
                        LOGGER.debug(
                            "Found existing judgment for docId: {}, score: {}, storedContextFields: {}",
                            docId,
                            score,
                            storedContextFields
                        );

                        synchronized (docIdToScore) {
                            docIdToScore.put(docId, score);
                        }
                        unprocessedDocIds.remove(docId);
                    }
                    checkCompletion();
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error(
                        "Failed to check judgment cache for queryTextWithReference: {} and docId: {}",
                        queryTextWithReference,
                        docId,
                        e
                    );
                    checkCompletion();
                }

                private void checkCompletion() {
                    if (pendingChecks.decrementAndGet() == 0) {
                        listener.onResponse(new ArrayList<>(unprocessedDocIds));
                    }
                }
            });
        }
    }

    /**
     * Add new judgment cache entry with llm judgment score
     */
    private void updateJudgmentCache(
        String compositeKey,
        String queryText,
        List<String> contextFields,
        String ratingScore,
        String modelId
    ) {
        JudgmentCache judgmentCache = new JudgmentCache(
            UUID.randomUUID().toString(),
            TimeUtils.getTimestamp(),
            queryText,
            compositeKey,
            contextFields,
            ratingScore,
            modelId
        );
        judgmentCacheDao.putJudgementCache(
            judgmentCache,
            ActionListener.wrap(
                response -> LOGGER.debug(
                    "Successfully updated judgment cache for queryText: {} and compositeKey: {}, contextFields: {}",
                    queryText,
                    compositeKey,
                    contextFields
                ),
                e -> LOGGER.error(
                    "Failed to update judgment cache for queryText: {} and compositeKey: {}, contextFields: {}",
                    queryText,
                    compositeKey,
                    contextFields,
                    e
                )
            )
        );
    }

}
