package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
class TestIdentifier {

    @JsonProperty("displayName")
    public String displayName;
    @JsonProperty("uniqueId")
    public List<Map> uniqueId;
    @JsonProperty("parentId")
    public Object parentId;
    @JsonProperty("legacyReportingName")
    public String legacyReportingName;
    @JsonProperty("type")
    public String type;
    @JsonProperty("source")
    public Object source;
    @JsonIgnore
    public Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    @JsonAnyGetter
    Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}