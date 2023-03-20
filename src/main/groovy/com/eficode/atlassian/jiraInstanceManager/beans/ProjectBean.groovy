package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.databind.ObjectMapper

class ProjectBean {
    public String returnUrl
    public Integer projectId
    public String projectKey
    public String projectName
    public Map remoteProjectLinks
    public Map<String, Object> unknownParameters = [:]

    static final ObjectMapper objectMapper = new ObjectMapper()

    ProjectBean(){}

    static ProjectBean fromMap(Map rawMap) {
        return objectMapper.convertValue(rawMap, ProjectBean.class)
    }


    @JsonAnyGetter
    Map<String, Object> getUnknownParameters() {
        return unknownParameters;
    }

    @JsonAnySetter
    void set(String name, Object value) {
        unknownParameters.put(name, value);

    }
}