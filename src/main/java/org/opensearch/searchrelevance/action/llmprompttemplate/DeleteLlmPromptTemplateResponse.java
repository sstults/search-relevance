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

import java.io.IOException;

/**
 * Response for deleting an LLM prompt template
 */
public class DeleteLlmPromptTemplateResponse extends ActionResponse implements ToXContentObject {
    
    private final String templateId;
    private final String result;
    private final boolean found;
    
    public DeleteLlmPromptTemplateResponse(String templateId, String result, boolean found) {
        this.templateId = templateId;
        this.result = result;
        this.found = found;
    }
    
    public DeleteLlmPromptTemplateResponse(StreamInput in) throws IOException {
        super(in);
        this.templateId = in.readString();
        this.result = in.readString();
        this.found = in.readBoolean();
    }
    
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(templateId);
        out.writeString(result);
        out.writeBoolean(found);
    }
    
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("template_id", templateId);
        builder.field("result", result);
        builder.field("found", found);
        builder.endObject();
        return builder;
    }
    
    public String getTemplateId() {
        return templateId;
    }
    
    public String getResult() {
        return result;
    }
    
    public boolean isFound() {
        return found;
    }
}
