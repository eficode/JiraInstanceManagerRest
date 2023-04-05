package com.eficode.atlassian.jiraInstanceManager.beans

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.GenericType
import kong.unirest.HttpResponse
import kong.unirest.UnirestInstance;

/**
 * Represents a ScriptRunenr scheduled job
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
class SrJob {

    @JsonProperty("@class")
    public String _class;
    @JsonProperty("id")
    public String id;
    @JsonProperty("version")
    public Integer version;
    @JsonProperty("ownedBy")
    public Object ownedBy;
    @JsonProperty("scheduleType")
    public String scheduleType;
    @JsonProperty("disabled")
    public Boolean disabled;
    @JsonProperty("name")
    public String name;
    @JsonProperty("nextRunTime")
    public Long nextRunTime;
    @JsonProperty("FIELD_USER_ID")
    public String fieldUserId;
    @JsonProperty("FIELD_INTERVAL")
    public String fieldInterval;
    @JsonProperty("FIELD_NOTES")
    public String fieldNotes;
    @JsonProperty("FIELD_RUN_ONCE_DATE")
    public Object fieldRunOnceDate;
    @JsonProperty("FIELD_JOB_CODE")
    public FieldJobCode fieldJobCode;
    @JsonProperty("canned-script")
    public String cannedScript;
    @JsonIgnore
    public Map<String, Object> additionalProperties = [:]
    static final ObjectMapper objectMapper = new ObjectMapper()


    @JsonAnyGetter
    Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }


    static SrJob fromMap(Map rawMap) {
        return objectMapper.convertValue(rawMap, SrJob.class)
    }

    static boolean deleteJob(JiraInstanceManagerRest jim, String jobId) {

        UnirestInstance unirestInstance = jim.getUnirest()

        HttpResponse response = unirestInstance.delete("/rest/scriptrunner/latest/scheduled-jobs/" + jobId).cookie(jim.acquireWebSudoCookies()).asEmpty()

        unirestInstance.shutDown()

       return response.status == 204

    }

    static SrJob createJob(JiraInstanceManagerRest jim, String jobNote, String userKey, String cron, String scriptPath) {

        UnirestInstance unirestInstance = jim.getUnirest()


        HttpResponse createResponse = unirestInstance
                .post("/rest/scriptrunner/latest/scheduled-jobs/com.onresolve.scriptrunner.canned.jira.jobs.JiraCustomScheduledJob")
                .contentType("application/json")
                .cookie(jim.acquireWebSudoCookies())
                .body([
                        FIELD_NOTES    : jobNote,
                        FIELD_USER_ID  : userKey,
                        FIELD_INTERVAL : cron,
                        FIELD_JOB_CODE : [
                                script    : null,
                                scriptPath: scriptPath
                        ],
                        "canned-script": "com.onresolve.scriptrunner.canned.jira.jobs.JiraCustomScheduledJob"
                ])
                .asEmpty()
                .ifFailure(Error.class, r -> {

                    unirestInstance.shutDown()
                    throw new InputMismatchException("Error creating SR Job \"$jobNote\": " + r.body.toString())
                })

        assert createResponse.status == 200: "Error creating SR Job \"$jobNote\""
        unirestInstance.shutDown()

        ArrayList<SrJob> srJobs = getJobs(jim)

        SrJob job = srJobs.find { it.fieldNotes == jobNote && it.fieldJobCode.scriptPath == scriptPath }
        assert job: "Could not find new job with jobNote:" + jobNote


        return job

    }

    static ArrayList<SrJob> getJobs(JiraInstanceManagerRest jim) {

        UnirestInstance unirestInstance = jim.getUnirest()


        HttpResponse<ArrayList<Map>> response = unirestInstance.get("/rest/scriptrunner/latest/scheduled-jobs?").cookie(jim.acquireWebSudoCookies()).asObject(new GenericType<ArrayList<Map>>() {
        })

        assert response.status == 200: "Error getting SR Jobs"

        unirestInstance.shutDown()

        ArrayList<SrJob> jobs = response.body.collect { fromMap(it) }

        return jobs

    }


    @JsonInclude(JsonInclude.Include.NON_NULL)
    class FieldJobCode {

        @JsonProperty("script")
        public Object script;
        @JsonProperty("scriptPath")
        public String scriptPath;
        @JsonProperty("parameters")
        public Map parameters;
        @JsonIgnore
        public Map<String, Object> additionalProperties = [:]

        @JsonAnyGetter
        Map<String, Object> getAdditionalProperties() {
            return this.additionalProperties;
        }

        @JsonAnySetter
        void setAdditionalProperty(String name, Object value) {
            this.additionalProperties.put(name, value);
        }

    }

}
