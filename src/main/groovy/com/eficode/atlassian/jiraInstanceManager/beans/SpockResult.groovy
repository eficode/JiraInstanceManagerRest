package com.eficode.atlassian.jiraInstanceManager.beans


import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpockResult {

    static ObjectMapper objectMapper = new ObjectMapper()

    @JsonProperty("events")
    public List<SpockEvent> events;
    @JsonIgnore
    public Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();


    String toString() {
        return events.findAll { it.testIdentifier.type == "TEST" && it.eventType == SpockEvent.EventType.RecordedExecutionFinishedEvent }.collect { it.toString() + "\n" }.join()
    }

    /**
     * Get all SpockEvents that are successfully executed tests
     * @return
     */
    ArrayList<SpockEvent> getSuccessfulTests() {
        finishedTests.findAll { it.isSuccessful() }
    }

    /**
     * Get all SpockEvents that failed tests
     * @return
     */
    ArrayList<SpockEvent> getFailedTests() {
        finishedTests.findAll { !it.isSuccessful() }
    }


    /**
     * Get all tests that finished, regardless if they where successful
     * @return
     */
    ArrayList<SpockEvent> getFinishedTests() {
        events.findAll { it.eventType == SpockEvent.EventType.RecordedExecutionFinishedEvent && it.testIdentifier.type == "TEST" }
    }

    /**
     * Returnes true if all tests where successful
     * @return
     */
    boolean isSuccessful() {
        return events.every { it.isSuccessful() }
    }

    @JsonAnyGetter
    Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }


    static SpockResult fromString(String rawJson) {
        return objectMapper.readValue(rawJson, SpockResult)
    }

}