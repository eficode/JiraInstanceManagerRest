package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestExecutionResult {


    /**
     * Status of executing a single test or container.
     */
    public enum Status {

        /**
         * Indicates that the execution of a test or container was
         * <em>successful</em>.
         */
        SUCCESSFUL,

        /**
         * Indicates that the execution of a test or container was
         * <em>aborted</em> (started but not finished).
         */
        ABORTED,

        /**
         * Indicates that the execution of a test or container <em>failed</em>.
         */
        FAILED;

    }

    @JsonProperty("status")
    public Status status;
    @JsonProperty("throwable")
    public Map throwable;
    @JsonIgnore
    public Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    @JsonAnyGetter
    Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}