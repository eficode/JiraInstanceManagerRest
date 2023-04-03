package com.eficode.atlassian.jiraInstanceManager.model

import com.eficode.atlassian.jiraInstanceManager.beans.RecordedExecutionFinishedEvent
import com.eficode.atlassian.jiraInstanceManager.beans.TestExecutionResult
import com.eficode.atlassian.jiraInstanceManager.beans.TestIdentifier
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonSubTypes(@JsonSubTypes.Type(value = RecordedExecutionFinishedEvent.class, name = "RecordedExecutionFinishedEvent"))
trait RecordedTestExecutionEvent {


    TestIdentifier testIdentifier
    TestExecutionResult testExecutionResult


    static final ObjectMapper objectMapper = new ObjectMapper()


    static <T> T fromString(Class<T> toValueType, String rawJson) {

        return objectMapper.readValue(rawJson, toValueType)
    }

    static <T> T fromMap(Class<T> toValueType, Map rawMap) {

        return objectMapper.convertValue(rawMap, toValueType)
    }


}