package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper

class ObjectSchemaBean {

    public Integer id
    public String name
    public String objectSchemaKey
    public String status
    public String created
    public String updated
    public Integer objectCount
    public Integer objectTypeCount
    public Integer archivedObjectCount
    public String description

    static final ObjectMapper objectMapper = new ObjectMapper()

    static ObjectSchemaBean fromMap(Map rawMap) {
        return objectMapper.convertValue(rawMap, ObjectSchemaBean.class)
    }


    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();


    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}

