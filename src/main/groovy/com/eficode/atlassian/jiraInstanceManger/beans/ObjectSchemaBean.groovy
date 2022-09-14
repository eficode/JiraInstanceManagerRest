package com.eficode.atlassian.jiraInstanceManger.beans

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
    public String description

    static final ObjectMapper objectMapper = new ObjectMapper()

    static ObjectSchemaBean fromMap(Map rawMap) {
        return objectMapper.convertValue(rawMap, ObjectSchemaBean.class)
    }

}

