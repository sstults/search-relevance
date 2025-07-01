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
 * Response for putting an LLM prompt template
 */
public class PutLlmPromptTemplateResponse extends ActionResponse implements ToXContentObject {
    
    private final String templateId;
    private final String result;
    
    public PutLlmPromptTemplateResponse(String templateId, String result) {
        this.templateId = templateId;
        this.result = result;
    }
    
    public PutLlmPromptTemplateResponse(StreamInput in) throws IOException {
        super(in);
        this.templateId = in.readString();
        this.result = in.readString();
    }
    
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(templateId);
        out.writeString(result);
    }
    
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("template_id", templateId);
        builder.field("result", result);
        builder.endObject();
        return builder;
    }
    
    public String getTemplateId() {
        return templateId;
    }
    
    public String getResult() {
        return result;
    }
}
