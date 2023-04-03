package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestIdentifier {

    @JsonProperty("displayName")
    private String displayName;
    @JsonProperty("uniqueId")
    private List<Map> uniqueId;
    @JsonProperty("parentId")
    private Object parentId;
    @JsonProperty("legacyReportingName")
    private String legacyReportingName;
    @JsonProperty("type")
    private String type;
    @JsonProperty("source")
    private Object source;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    @JsonProperty("displayName")
    public String getDisplayName() {
        return displayName;
    }

    @JsonProperty("displayName")
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @JsonProperty("uniqueId")
    public List<Map> getUniqueId() {
        return uniqueId;
    }

    @JsonProperty("uniqueId")
    public void setUniqueId(List<Map> uniqueId) {
        this.uniqueId = uniqueId;
    }

    @JsonProperty("parentId")
    public Object getParentId() {
        return parentId;
    }

    @JsonProperty("parentId")
    public void setParentId(Object parentId) {
        this.parentId = parentId;
    }

    @JsonProperty("legacyReportingName")
    public String getLegacyReportingName() {
        return legacyReportingName;
    }

    @JsonProperty("legacyReportingName")
    public void setLegacyReportingName(String legacyReportingName) {
        this.legacyReportingName = legacyReportingName;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("source")
    public Object getSource() {
        return source;
    }

    @JsonProperty("source")
    public void setSource(Object source) {
        this.source = source;
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