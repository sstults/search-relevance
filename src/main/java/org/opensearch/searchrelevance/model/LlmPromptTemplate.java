/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.searchrelevance.model;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

/**
 * Model class for LLM prompt templates
 */
public class LlmPromptTemplate implements ToXContentObject, Writeable {
    
    public static final String TEMPLATE_ID_FIELD = "template_id";
    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String TEMPLATE_FIELD = "template";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    
    private final String templateId;
    private final String name;
    private final String description;
    private final String template;
    private final Long createdTime;
    private final Long lastUpdatedTime;
    
    public LlmPromptTemplate(String templateId, String name, String description, String template, Long createdTime, Long lastUpdatedTime) {
        this.templateId = templateId;
        this.name = name;
        this.description = description;
        this.template = template;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
    }
    
    public LlmPromptTemplate(StreamInput input) throws IOException {
        this.templateId = input.readString();
        this.name = input.readString();
        this.description = input.readOptionalString();
        this.template = input.readString();
        this.createdTime = input.readOptionalLong();
        this.lastUpdatedTime = input.readOptionalLong();
    }
    
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(templateId);
        out.writeString(name);
        out.writeOptionalString(description);
        out.writeString(template);
        out.writeOptionalLong(createdTime);
        out.writeOptionalLong(lastUpdatedTime);
    }
    
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(TEMPLATE_ID_FIELD, templateId);
        builder.field(NAME_FIELD, name);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        builder.field(TEMPLATE_FIELD, template);
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime);
        }
        if (lastUpdatedTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdatedTime);
        }
        builder.endObject();
        return builder;
    }
    
    public static LlmPromptTemplate parse(XContentParser parser) throws IOException {
        String templateId = null;
        String name = null;
        String description = null;
        String template = null;
        Long createdTime = null;
        Long lastUpdatedTime = null;
        
        XContentParser.Token token = parser.currentToken();
        if (token != XContentParser.Token.START_OBJECT) {
            token = parser.nextToken();
        }
        
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                String fieldName = parser.currentName();
                token = parser.nextToken();
                
                switch (fieldName) {
                    case TEMPLATE_ID_FIELD:
                        templateId = parser.text();
                        break;
                    case NAME_FIELD:
                        name = parser.text();
                        break;
                    case DESCRIPTION_FIELD:
                        description = parser.text();
                        break;
                    case TEMPLATE_FIELD:
                        template = parser.text();
                        break;
                    case CREATED_TIME_FIELD:
                        createdTime = parser.longValue();
                        break;
                    case LAST_UPDATED_TIME_FIELD:
                        lastUpdatedTime = parser.longValue();
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }
        }
        
        return new LlmPromptTemplate(templateId, name, description, template, createdTime, lastUpdatedTime);
    }
    
    // Getters
    public String getTemplateId() {
        return templateId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getTemplate() {
        return template;
    }
    
    public Long getCreatedTime() {
        return createdTime;
    }
    
    public Long getLastUpdatedTime() {
        return lastUpdatedTime;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LlmPromptTemplate that = (LlmPromptTemplate) o;
        return Objects.equals(templateId, that.templateId) &&
               Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(template, that.template) &&
               Objects.equals(createdTime, that.createdTime) &&
               Objects.equals(lastUpdatedTime, that.lastUpdatedTime);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(templateId, name, description, template, createdTime, lastUpdatedTime);
    }
    
    @Override
    public String toString() {
        return "LlmPromptTemplate{" +
               "templateId='" + templateId + '\'' +
               ", name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", template='" + template + '\'' +
               ", createdTime=" + createdTime +
               ", lastUpdatedTime=" + lastUpdatedTime +
               '}';
    }
}
