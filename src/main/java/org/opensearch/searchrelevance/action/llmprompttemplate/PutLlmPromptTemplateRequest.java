/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.llmprompttemplate;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.searchrelevance.model.LlmPromptTemplate;

/**
 * Request for putting an LLM prompt template
 */
public class PutLlmPromptTemplateRequest extends ActionRequest {

    private String templateId;
    private LlmPromptTemplate template;

    public PutLlmPromptTemplateRequest() {}

    public PutLlmPromptTemplateRequest(String templateId, LlmPromptTemplate template) {
        this.templateId = templateId;
        this.template = template;
    }

    public PutLlmPromptTemplateRequest(StreamInput in) throws IOException {
        super(in);
        this.templateId = in.readString();
        this.template = new LlmPromptTemplate(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(templateId);
        template.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;

        if (templateId == null || templateId.trim().isEmpty()) {
            validationException = addValidationError("template_id is required", validationException);
        }

        if (template == null) {
            validationException = addValidationError("template is required", validationException);
        } else {
            if (template.getName() == null || template.getName().trim().isEmpty()) {
                validationException = addValidationError("template name is required", validationException);
            }
            if (template.getTemplate() == null || template.getTemplate().trim().isEmpty()) {
                validationException = addValidationError("template content is required", validationException);
            }
        }

        return validationException;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public LlmPromptTemplate getTemplate() {
        return template;
    }

    public void setTemplate(LlmPromptTemplate template) {
        this.template = template;
    }

    public static PutLlmPromptTemplateRequest fromXContent(XContentParser parser, String templateId) throws IOException {
        LlmPromptTemplate template = LlmPromptTemplate.parse(parser);
        // Create a new template with the provided templateId and current timestamp
        long currentTime = System.currentTimeMillis();
        LlmPromptTemplate templateWithId = new LlmPromptTemplate(
            templateId,
            template.getName(),
            template.getDescription(),
            template.getTemplate(),
            template.getCreatedTime() != null ? template.getCreatedTime() : currentTime,
            currentTime
        );
        return new PutLlmPromptTemplateRequest(templateId, templateWithId);
    }
}
