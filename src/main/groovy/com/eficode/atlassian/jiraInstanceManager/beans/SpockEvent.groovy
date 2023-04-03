package com.eficode.atlassian.jiraInstanceManager.beans



import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpockEvent {

    @JsonProperty("@class")
    private String _class;
    @JsonProperty("testIdentifier")
    private TestIdentifier testIdentifier;
    @JsonProperty("testExecutionResult")
    private TestExecutionResult testExecutionResult;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    @JsonProperty("@class")
    public String getClass_() {
        return _class;
    }

    @JsonProperty("@class")
    public void setClass_(String _class) {
        this._class = _class;
    }

    @JsonProperty("testIdentifier")
    public TestIdentifier getTestIdentifier() {
        return testIdentifier;
    }

    @JsonProperty("testIdentifier")
    public void setTestIdentifier(TestIdentifier testIdentifier) {
        this.testIdentifier = testIdentifier;
    }

    @JsonProperty("testExecutionResult")
    public TestExecutionResult getTestExecutionResult() {
        return testExecutionResult;
    }

    @JsonProperty("testExecutionResult")
    public void setTestExecutionResult(TestExecutionResult testExecutionResult) {
        this.testExecutionResult = testExecutionResult;
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