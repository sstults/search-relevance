/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.llmprompttemplate;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/**
 * Request for getting an LLM prompt template
 */
public class GetLlmPromptTemplateRequest extends ActionRequest {

    private String templateId;

    public GetLlmPromptTemplateRequest() {}

    public GetLlmPromptTemplateRequest(String templateId) {
        this.templateId = templateId;
    }

    public GetLlmPromptTemplateRequest(StreamInput in) throws IOException {
        super(in);
        this.templateId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(templateId);
    }

    @Override
    public ActionRequestValidationException validate() {
        // template_id is optional - if null, it means search all templates
        return null;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }
}
