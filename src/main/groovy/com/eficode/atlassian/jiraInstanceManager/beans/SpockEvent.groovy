package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty


@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpockEvent {

    @JsonProperty("@class")
    public EventType eventType;
    @JsonProperty("testIdentifier")
    public TestIdentifier testIdentifier;
    @JsonProperty("testExecutionResult")
    public TestExecutionResult testExecutionResult;
    @JsonIgnore
    public Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    String getDisplayName() {

        return testIdentifier?.displayName

    }

    String getMethodName() {
        return testIdentifier?.source?.methodName
    }

    String toString() {

        String out = methodName + ":" + testExecutionResult?.status ?: "null"

        if (testExecutionResult?.throwable?.stackTrace) {
            ArrayList<Map>stackTraces = testExecutionResult?.throwable?.stackTrace as ArrayList<Map>
            out += "\n\t" + testExecutionResult.throwable.message.replace("\n", "") + "\n"

            //at com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRestSpec.Test runSpockTest(JiraInstanceManagerRestSpec.groovy:230)

            stackTraces.each {
                out += "\t"*2 + "at ${it.className}.${it.methodName}(" + it.fileName + ":" + it.lineNumber + ")"
             }
        }

        return out
    }

    boolean isSuccessful() {
        return testExecutionResult == null || testExecutionResult.status == TestExecutionResult.Status.SUCCESSFUL
    }

    public enum EventType {
      
        @JsonProperty("com.onresolve.scriptrunner.testrunner.event.RecordedExecutionStartedEvent")
        RecordedExecutionStartedEvent("RecordedExecutionStartedEvent", "com.onresolve.scriptrunner.testrunner.event.RecordedExecutionStartedEvent"),
        @JsonProperty("com.onresolve.scriptrunner.testrunner.event.RecordedExecutionFinishedEvent")
        RecordedExecutionFinishedEvent("RecordedExecutionFinishedEvent", "com.onresolve.scriptrunner.testrunner.event.RecordedExecutionFinishedEvent"),
        @JsonProperty("com.onresolve.scriptrunner.testrunner.event.RecordedDynamicTestRegisteredEvent")
        RecordedDynamicTestRegisteredEvent("RecordedDynamicTestRegisteredEvent", "com.onresolve.scriptrunner.testrunner.event.RecordedDynamicTestRegisteredEvent"),
        @JsonProperty("com.onresolve.scriptrunner.testrunner.event.RecordedExecutionSkippedEvent")
        RecordedExecutionSkippedEvent("RecordedExecutionSkippedEvent", "com.onresolve.scriptrunner.testrunner.event.RecordedExecutionSkippedEvent"),
        @JsonProperty("com.onresolve.scriptrunner.testrunner.event.RecordedTestExecutionEvent")
        RecordedTestExecutionEvent("RecordedTestExecutionEvent", "com.onresolve.scriptrunner.testrunner.event.RecordedTestExecutionEvent"),
        @JsonProperty("com.onresolve.scriptrunner.testrunner.event.TestExecutionEventsRecorder")
        TestExecutionEventsRecorder("TestExecutionEventsRecorder", "com.onresolve.scriptrunner.testrunner.event.TestExecutionEventsRecorder"),
        @JsonProperty("com.onresolve.scriptrunner.testrunner.event.TestExecutionEventsRecording")
        TestExecutionEventsRecording("TestExecutionEventsRecording", "com.onresolve.scriptrunner.testrunner.event.TestExecutionEventsRecording")

        
        public String eventName
        public String eventClass


        EventType(final String eventName, final String eventClass) {
            this.eventName = eventName
            this.eventClass = eventClass

        }
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
