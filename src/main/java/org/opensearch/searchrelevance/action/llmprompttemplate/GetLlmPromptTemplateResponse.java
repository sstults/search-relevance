/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.searchrelevance.action.llmprompttemplate;

import org.opensearch.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.searchrelevance.model.LlmPromptTemplate;

import java.io.IOException;

/**
 * Response for getting an LLM prompt template
 */
public class GetLlmPromptTemplateResponse extends ActionResponse implements ToXContentObject {
    
    private final LlmPromptTemplate template;
    private final boolean found;
    
    public GetLlmPromptTemplateResponse(LlmPromptTemplate template, boolean found) {
        this.template = template;
        this.found = found;
    }
    
    public GetLlmPromptTemplateResponse(StreamInput in) throws IOException {
        super(in);
        this.found = in.readBoolean();
        this.template = found ? new LlmPromptTemplate(in) : null;
    }
    
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(found);
        if (found) {
            template.writeTo(out);
        }
    }
    
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("found", found);
        if (found && template != null) {
            builder.field("template", template);
        }
        builder.endObject();
        return builder;
    }
    
    public LlmPromptTemplate getTemplate() {
        return template;
    }
    
    public boolean isFound() {
        return found;
    }
}
