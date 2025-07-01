/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.llmprompttemplate;

import java.io.IOException;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.searchrelevance.model.LlmPromptTemplate;

/**
 * Response for getting an LLM prompt template
 */
public class GetLlmPromptTemplateResponse extends ActionResponse implements ToXContentObject {

    private final LlmPromptTemplate template;
    private final SearchResponse searchResponse;
    private final boolean found;

    public GetLlmPromptTemplateResponse(LlmPromptTemplate template, boolean found) {
        this.template = template;
        this.searchResponse = null;
        this.found = found;
    }

    public GetLlmPromptTemplateResponse(SearchResponse searchResponse, boolean found) {
        this.template = null;
        this.searchResponse = searchResponse;
        this.found = found;
    }

    public GetLlmPromptTemplateResponse(StreamInput in) throws IOException {
        super(in);
        this.found = in.readBoolean();
        boolean hasTemplate = in.readBoolean();
        if (hasTemplate) {
            this.template = new LlmPromptTemplate(in);
            this.searchResponse = null;
        } else {
            this.template = null;
            this.searchResponse = found ? new SearchResponse(in) : null;
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(found);
        out.writeBoolean(template != null);
        if (template != null) {
            template.writeTo(out);
        } else if (searchResponse != null) {
            searchResponse.writeTo(out);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (template != null) {
            // Single template response
            builder.startObject();
            builder.field("found", found);
            if (found && template != null) {
                builder.field("template", template);
            }
            builder.endObject();
        } else if (searchResponse != null) {
            // Search response - return the search response directly
            return searchResponse.toXContent(builder, params);
        } else {
            builder.startObject();
            builder.field("found", false);
            builder.endObject();
        }
        return builder;
    }

    public LlmPromptTemplate getTemplate() {
        return template;
    }

    public SearchResponse getSearchResponse() {
        return searchResponse;
    }

    public boolean isFound() {
        return found;
    }
}
