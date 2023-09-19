package com.eficode.atlassian.jiraInstanceManager.beans

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.Cookies
import kong.unirest.GenericType
import kong.unirest.HttpResponse
import kong.unirest.UnirestInstance
import org.apache.groovy.json.internal.LazyMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class ScriptFieldBean {

    static Logger log = LoggerFactory.getLogger(FieldBean.class)
    static final ObjectMapper objectMapper = new ObjectMapper()


    @JsonProperty("id")
    public String id;
    @JsonProperty("version")
    public Integer version;
    @JsonProperty("ownedBy")
    public Object ownedBy;
    @JsonProperty("fieldConfigurationSchemeId")
    public Integer fieldConfigurationSchemeId;
    @JsonProperty("customFieldId")
    public Integer customFieldId;
    @JsonProperty("note")
    public Object note;
    @JsonProperty("fieldConfigSchemeName")
    public String fieldConfigSchemeName;
    @JsonProperty("searcherName")
    public String searcherName;
    @JsonProperty("relatedProjects")
    public List<Map> relatedProjects;
    @JsonProperty("isAllProjects")
    public Boolean isAllProjects;
    @JsonProperty("customTemplate")
    public Object customTemplate;
    @JsonProperty("modelTemplate")
    public String modelTemplate;
    @JsonProperty("name")
    public String name;
    @JsonProperty("desc")
    public Object desc;
    @JsonProperty("FIELD_PREVIEW_ISSUE")
    public Object fieldPreviewIssue;
    @JsonProperty("FIELD_SCRIPT_FILE_OR_SCRIPT")
    @JsonAlias("CONFIGURATION_SCRIPT")
    public Map fieldScriptFileOrScript;
    @JsonProperty("FIELD_IS_MULTIPLE")
    public boolean fieldIsMultiple;
    @JsonProperty("canned-script")
    public String cannedScript;
    @JsonProperty("TRANSITION_STATE")
    public String transitionState //Used for "Date of First Transition" fields

    @JsonProperty("SEARCH_FIELDS")
    public List<String> searchFields; //Used for "Issue Picker" fields
    @JsonProperty("FIELD_PLACEHOLDER")
    public String fieldPlaceholder; //Used for "Issue Picker" fields
    @JsonProperty("currentJql")
    public String currentJql; //Used for "Issue Picker" fields

    @JsonProperty("FIELD_TIMES_IN_STATUS")
    public ArrayList<String> fieldTimesInStatus; //Used for "No. of Times In Status" fields

    @JsonProperty("FIELD_PARENT_ISSUE_EXTRACTORS")
    public ArrayList<String> fieldParentIssueExtractors; //Used for "Show parent issue in hierarchy" fields
    @JsonProperty("FIELD_TARGET_ISSUE_TYPE")
    public String fieldTargetIssueType; //Used for "Show parent issue in hierarchy" fields

    @JsonProperty("FIELD_CONN_NAME")
    public String fieldConnName; //Used for "Database Picker" fields
    @JsonProperty("VALIDATION_SQL")
    public String validationSQL; //Used for "Database Picker" fields
    @JsonProperty("SEARCH_SQL")
    public String searchSQL; //Used for "Database Picker" fields



    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    @JsonIgnore()
    JiraInstanceManagerRest jiraInstance

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    @JsonSetter("FIELD_IS_MULTIPLE")
    public void setFieldIsMultiple(String onOrOff) {
        fieldIsMultiple = (onOrOff == "on")
    }

    String toString() {
        return "$name ($customFieldId)"
    }


    static ArrayList<ScriptFieldBean> getScriptFields(JiraInstanceManagerRest jim) {

        Cookies cookies = jim.acquireWebSudoCookies()
        HttpResponse response = jim.unirest.get("/rest/scriptrunner-jira/latest/scriptfields?").cookie(cookies).asObject(new GenericType<ArrayList<Map>>() {
        })

        assert response.status == 200: "Error getting ScriptFields from " + jim.unirest.config().defaultBaseUrl
        ArrayList<Map> rawFields = response.body

        ArrayList<ScriptFieldBean> scriptFieldBeans = objectMapper.convertValue(rawFields, new TypeReference<ArrayList<ScriptFieldBean>>() {
        })
        scriptFieldBeans.each { it.jiraInstance = jim }

        return scriptFieldBeans
    }

    boolean isUsingInlineScript() {
        return fieldScriptFileOrScript?.script
    }

    boolean isUsingScriptFile() {
        return fieldScriptFileOrScript?.scriptPath
    }

    String getScriptBody() {

        if (usingInlineScript) {
            return fieldScriptFileOrScript.script
        } else if (usingScriptFile) {

            String body = jiraInstance.getScriptrunnerFile(fieldScriptFileOrScript?.scriptPath?.toString())
            assert body != "": "Error getting script body for script:" + fieldScriptFileOrScript?.scriptPath

            return body

        }else if (cannedScript.endsWith("DateOfFirstTransitionScriptField")) {
            log.debug("Date oF First Transition fields dont have a script body, only transitionState parameter")
            return ""
        }else if (cannedScript.endsWith("NoOfTimesInStatusScriptField")) {
            log.debug("No. of Times In Status fields dont have a script body, only fieldTimesInStatus parameter")
            return ""
        }else if (cannedScript.endsWith("ParentIssueScriptField")) {
            log.debug("Show parent issue in hierarchy fields dont have a script body, only fieldParentIssueExtractors and fieldTargetIssueType parameter")
            return ""
        }else if (cannedScript.endsWith("TimeOfLastStatusChangeScriptField")) {
            log.debug("Time of Last Status Change fields dont have a script body")
            return ""
        }else if (cannedScript.endsWith("DbPickerCannedField")) {
            log.debug("Database Picker Fields do not require Script Body")
            return ""
        }else if (cannedScript.endsWith("IssuePickerCannedFieldConfig")) {
            log.debug("Issue Picker Fields do not require Script Body")
            return ""
        }

        throw new InputMismatchException("Could not determine ScriptBody for ScriptField: ${this.toString()} ")


    }

    /**
     * Get the last 15 executions of the script, equivalent of clicking the link in the History column in SR/Fields
     * @return
     */
    ArrayList<ScriptFieldExecution> getExecutions() {

        HttpResponse<ArrayList<Map>> rawResponse = jiraInstance.unirest.get("/rest/scriptrunner/latest/diagnostics/results?functionId=$fieldConfigurationSchemeId").asObject(new GenericType<ArrayList<Map>>() {})
        assert rawResponse.status == 200 : "Error getting execution history for $this"
        ArrayList<Map> rawExecutions = rawResponse.body
        rawExecutions.each {
            it.payload = objectMapper.readValue(it.payload.toString(), new TypeReference<Map>() {})
        }
        ArrayList<ScriptFieldExecution> executions = objectMapper.convertValue(rawExecutions, new TypeReference<ArrayList<ScriptFieldExecution>>() {})

        return executions


    }


    static class ScriptFieldExecution {

        @JsonProperty("key")
        String key
        @JsonProperty("created")
        long created
        @JsonProperty("cpuTime")
        long cpuTime
        @JsonProperty("millisecondsTaken")
        long millisecondsTaken
        @JsonProperty("exception")
        String exception
        @JsonProperty("payload")
        Map payload
        @JsonProperty("log")
        String log

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


}
