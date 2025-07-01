/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.searchrelevance.action.llmprompttemplate;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;

import static org.opensearch.action.ValidateActions.addValidationError;

/**
 * Request for deleting an LLM prompt template
 */
public class DeleteLlmPromptTemplateRequest extends ActionRequest {
    
    private String templateId;
    
    public DeleteLlmPromptTemplateRequest() {}
    
    public DeleteLlmPromptTemplateRequest(String templateId) {
        this.templateId = templateId;
    }
    
    public DeleteLlmPromptTemplateRequest(StreamInput in) throws IOException {
        super(in);
        this.templateId = in.readString();
    }
    
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(templateId);
    }
    
    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        
        if (templateId == null || templateId.trim().isEmpty()) {
            validationException = addValidationError("template_id is required", validationException);
        }
        
        return validationException;
    }
    
    public String getTemplateId() {
        return templateId;
    }
    
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }
}
