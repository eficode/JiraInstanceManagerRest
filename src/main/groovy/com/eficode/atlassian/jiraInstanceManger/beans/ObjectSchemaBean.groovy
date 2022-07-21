package com.eficode.atlassian.jiraInstanceManger.beans

import com.fasterxml.jackson.databind.ObjectMapper

class ObjectSchemaBean {

    Integer id
    String name
    String objectSchemaKey
    String status
    String created
    String updated
    Integer objectCount
    Integer objectTypeCount
    String description

    static final ObjectMapper objectMapper = new ObjectMapper()

    static ObjectSchemaBean fromMap(Map rawMap) {
        return objectMapper.convertValue(rawMap, ObjectSchemaBean.class)
    }

}

