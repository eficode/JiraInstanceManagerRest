package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.annotation.JsonProperty
import org.junit.platform.commons.util.Preconditions
import org.junit.platform.engine.TestExecutionResult.Status


import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestExecutionResult {

    @JsonProperty("status")
    private String status;
    @JsonProperty("throwable")
    private Object throwable;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(String status) {
        this.status = status;
    }

    @JsonProperty("throwable")
    public Object getThrowable() {
        return throwable;
    }

    @JsonProperty("throwable")
    public void setThrowable(Object throwable) {
        this.throwable = throwable;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}