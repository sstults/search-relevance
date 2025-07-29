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
 * RemoteSearchFailure represents failed remote search operations for analysis and potential retry.
 * This enables tracking and debugging of remote search connectivity issues.
 */
public class RemoteSearchFailure implements ToXContentObject {
    public static final String ID = "id";
    public static final String REMOTE_CONFIG_ID = "remoteConfigId";
    public static final String EXPERIMENT_ID = "experimentId";
    public static final String QUERY = "query";
    public static final String QUERY_TEXT = "queryText";
    public static final String ERROR_TYPE = "errorType";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String TIMESTAMP = "timestamp";
    public static final String STATUS = "status";

    /**
     * Error types for remote search failures
     */
    public enum ErrorType {
        CONNECTION_TIMEOUT,
        AUTH_FAILURE,
        INVALID_RESPONSE,
        RATE_LIMIT_EXCEEDED,
        REMOTE_SERVER_ERROR,
        NETWORK_ERROR,
        UNKNOWN_ERROR
    }

    /**
     * Status of the failure record
     */
    public enum Status {
        FAILED,
        RETRY_PENDING,
        RESOLVED
    }

    private final String id;
    private final String remoteConfigId;
    private final String experimentId;
    private final String query;
    private final String queryText;
    private final String errorType;
    private final String errorMessage;
    private final String timestamp;
    private final String status;

    public RemoteSearchFailure(
        String id,
        String remoteConfigId,
        String experimentId,
        String query,
        String queryText,
        String errorType,
        String errorMessage,
        String timestamp,
        String status
    ) {
        this.id = id;
        this.remoteConfigId = remoteConfigId;
        this.experimentId = experimentId;
        this.query = query;
        this.queryText = queryText;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.timestamp = timestamp;
        this.status = status;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id != null ? this.id : "");
        xContentBuilder.field(REMOTE_CONFIG_ID, this.remoteConfigId != null ? this.remoteConfigId : "");
        xContentBuilder.field(EXPERIMENT_ID, this.experimentId != null ? this.experimentId : "");
        xContentBuilder.field(QUERY, this.query != null ? this.query : "");
        xContentBuilder.field(QUERY_TEXT, this.queryText != null ? this.queryText : "");
        xContentBuilder.field(ERROR_TYPE, this.errorType != null ? this.errorType : "");
        xContentBuilder.field(ERROR_MESSAGE, this.errorMessage != null ? this.errorMessage : "");
        xContentBuilder.field(TIMESTAMP, this.timestamp != null ? this.timestamp : "");
        xContentBuilder.field(STATUS, this.status != null ? this.status : "");
        return xContentBuilder.endObject();
    }

    /**
     * Create a failure record from an exception
     */
    public static RemoteSearchFailure fromException(
        String id,
        String remoteConfigId,
        String experimentId,
        String query,
        String queryText,
        Exception exception,
        String timestamp
    ) {
        ErrorType errorType = categorizeException(exception);
        return new RemoteSearchFailure(
            id,
            remoteConfigId,
            experimentId,
            query,
            queryText,
            errorType.name(),
            exception.getMessage(),
            timestamp,
            Status.FAILED.name()
        );
    }

    /**
     * Categorize exception into error types
     */
    private static ErrorType categorizeException(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return ErrorType.UNKNOWN_ERROR;
        }

        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
            return ErrorType.CONNECTION_TIMEOUT;
        } else if (lowerMessage.contains("unauthorized") || lowerMessage.contains("authentication")) {
            return ErrorType.AUTH_FAILURE;
        } else if (lowerMessage.contains("rate limit") || lowerMessage.contains("too many requests")) {
            return ErrorType.RATE_LIMIT_EXCEEDED;
        } else if (lowerMessage.contains("server error") || lowerMessage.contains("500")) {
            return ErrorType.REMOTE_SERVER_ERROR;
        } else if (lowerMessage.contains("network") || lowerMessage.contains("connection")) {
            return ErrorType.NETWORK_ERROR;
        } else if (lowerMessage.contains("response") || lowerMessage.contains("parse")) {
            return ErrorType.INVALID_RESPONSE;
        } else {
            return ErrorType.UNKNOWN_ERROR;
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getRemoteConfigId() {
        return remoteConfigId;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public String getQuery() {
        return query;
    }

    public String getQueryText() {
        return queryText;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getStatus() {
        return status;
    }
}
