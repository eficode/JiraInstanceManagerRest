package com.eficode.atlassian.jiraInstanceManager.beans

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.core.Cookies
import kong.unirest.core.GenericType
import kong.unirest.core.HttpResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Matcher

@JsonInclude(JsonInclude.Include.NON_NULL)
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
    JiraInstanceManagerRest jim

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

    @JsonIgnore
    String toString() {
        return "$name ($customFieldId)"
    }

    @JsonIgnore
    String toJson(){
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
    }

    @JsonIgnore
    boolean isUsingInlineScript() {
        return fieldScriptFileOrScript?.script
    }

    @JsonIgnore
    boolean isUsingScriptFile() {
        return fieldScriptFileOrScript?.scriptPath
    }


    @JsonIgnore
    static ArrayList<ScriptFieldBean> getScriptFields(JiraInstanceManagerRest jim) {

        Cookies cookies = jim.acquireWebSudoCookies()
        HttpResponse response = jim.rest.get("/rest/scriptrunner-jira/latest/scriptfields?").cookie(cookies).asObject(new GenericType<ArrayList<Map>>() {
        })

        assert response.status == 200: "Error getting ScriptFields from " + jim.rest.config().defaultBaseUrl
        ArrayList<Map> rawFields = response.body

        ArrayList<ScriptFieldBean> scriptFieldBeans = getScriptFieldBeanFromMaps(rawFields, jim)

        return scriptFieldBeans
    }

    @JsonIgnore
    static ScriptFieldBean getScriptFieldBeanFromMap(Map map, JiraInstanceManagerRest jim) {
        return getScriptFieldBeanFromMaps([map], jim).find { true }
    }

    @JsonIgnore
    static ArrayList<ScriptFieldBean> getScriptFieldBeanFromMaps(ArrayList<Map> maps, JiraInstanceManagerRest jim) {

        ArrayList<ScriptFieldBean> scriptFieldBeans = objectMapper.convertValue(maps, new TypeReference<ArrayList<ScriptFieldBean>>() {
        })
        scriptFieldBeans.each { it.jim = jim }

        return scriptFieldBeans

    }


    /**
     * A simplified method for creating a "Custom script field" with default settings
     * @param jim The instance where the field will be created
     * @param fieldName Name of the field
     * @param inlineBody The script body that will be used as inline script
     * @param template The template used by the field, default is textarea
     * @return the new ScriptFieldBean
     */
    @JsonIgnore
    static ScriptFieldBean createCustomScriptField(JiraInstanceManagerRest jim, String fieldName, String inlineBody, String template = "textarea") {

        log.info("Creating scripted field:" + fieldName + " in " + jim.baseUrl)
        Map<String, Object> requestMap = [
                "name"                       : fieldName,
                "modelTemplate"              : template,
                "FIELD_SCRIPT_FILE_OR_SCRIPT":
                        [
                                "script"    : inlineBody,
                                "scriptPath": null
                        ],
                "canned-script"              : "com.onresolve.scriptrunner.canned.jira.fields.CustomCannedScriptField"
        ]


        HttpResponse<Map> httpResponse = jim.rest.post("/rest/scriptrunner-jira/latest/scriptfields/com.onresolve.scriptrunner.canned.jira.fields.CustomCannedScriptField")
                .cookie(jim.acquireWebSudoCookies())
                .contentType("application/json")
                .body(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestMap))
                .asObject(Map)

        log.debug("\tGot response:" + httpResponse.status)

        assert httpResponse.status == 200 : "Error creating ScriptFiled $fieldName, got reponse (${httpResponse.status}):" + httpResponse.body.toString()

        ScriptFieldBean scriptFieldBean = getScriptFieldBeanFromMap(httpResponse.body, jim)

        log.info("\tCreated script filed:" + scriptFieldBean.customFieldId)


        return scriptFieldBean

    }

    boolean deleteScriptField(boolean iAmSure) {

        assert iAmSure : "You are not sure that you want to delete ${customFieldId}"

        log.info("DELETING scripted field ${customFieldId}")

        HttpResponse httpResponse = jim.rest.delete("/rest/scriptrunner-jira/latest/scriptfields/$id")
                .cookie(jim.acquireWebSudoCookies())
                .contentType("application/json")
                .accept("application/json")
                .asEmpty()


        assert httpResponse.status == 204 : "Error deleting scripted field ${customFieldId}, got response ${httpResponse.status} " + httpResponse?.body?.toString()
        log.info("\tDeleted script field:" + customFieldId)
        return true


    }


    /**
     * Updates the script body (inline or file) of a scripted field
     * Consider backing up script first
     * @param newBody The new body of the script
     * @return true on success
     */
    @JsonIgnore
    boolean updateScriptBody(String newBody) {

        log.info("Updating script for scripted field:" + customFieldId)
        ScriptFieldBean scriptFieldBean = getScriptFields(jim).find { it.id == id }
        if (usingInlineScript) {

            log.info("\tFiled uses inline script")
            scriptFieldBean.fieldScriptFileOrScript.script = newBody
            scriptFieldBean.fieldPreviewIssue = null //Updating will break if issue no longer exists

            String scriptFieldBeanJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(scriptFieldBean)

            HttpResponse<Map> httpResponse = jim.rest.post("/rest/scriptrunner-jira/latest/scriptfields/com.onresolve.scriptrunner.canned.jira.fields.CustomCannedScriptField")
                    .cookie(jim.acquireWebSudoCookies())
                    .contentType("application/json")
                    .body(scriptFieldBeanJson)
                    .asObject(Map)

            log.debug("\tPosted updated to JIRA, got response:" + httpResponse.status)

            assert httpResponse.status == 200: "Error updating script field ${customFieldId}, got reponse (${httpResponse?.status}):" + httpResponse?.body

            ScriptFieldBean newFieldBean = getScriptFieldBeanFromMap(httpResponse.body, jim)

            assert newFieldBean.fieldScriptFileOrScript.script == newBody: "Error verifying script of Scripted Field after update"
            log.info("\tSuccessfully updated inline script for:" + newFieldBean.customFieldId)

            return true

        } else {
            log.info("\tField uses script file:" + fieldScriptFileOrScript?.scriptPath?.toString())
            assert jim.updateScriptrunnerFile(newBody, fieldScriptFileOrScript?.scriptPath?.toString()): "Error updating scriptField ${customFieldId} file:" + fieldScriptFileOrScript?.scriptPath?.toString()
            log.info("\tSuccessfully updated script file for:" + customFieldId)
            return true
        }

    }

    @JsonIgnore
    String getScriptBody() {

        if (usingInlineScript) {
            return fieldScriptFileOrScript.script
        } else if (usingScriptFile) {

            String body = jim.getScriptrunnerFile(fieldScriptFileOrScript?.scriptPath?.toString())
            assert body != "": "Error getting script body for script:" + fieldScriptFileOrScript?.scriptPath

            return body

        } else if (cannedScript.endsWith("DateOfFirstTransitionScriptField")) {
            log.debug("Date oF First Transition fields dont have a script body, only transitionState parameter")
            return ""
        } else if (cannedScript.endsWith("NoOfTimesInStatusScriptField")) {
            log.debug("No. of Times In Status fields dont have a script body, only fieldTimesInStatus parameter")
            return ""
        } else if (cannedScript.endsWith("ParentIssueScriptField")) {
            log.debug("Show parent issue in hierarchy fields dont have a script body, only fieldParentIssueExtractors and fieldTargetIssueType parameter")
            return ""
        } else if (cannedScript.endsWith("TimeOfLastStatusChangeScriptField")) {
            log.debug("Time of Last Status Change fields dont have a script body")
            return ""
        } else if (cannedScript.endsWith("DbPickerCannedField")) {
            log.debug("Database Picker Fields do not require Script Body")
            return ""
        } else if (cannedScript.endsWith("IssuePickerCannedFieldConfig")) {
            log.debug("Issue Picker Fields do not require Script Body")
            return ""
        }

        throw new InputMismatchException("Could not determine ScriptBody for ScriptField: ${this.toString()} ")


    }

    /**
     * Get the last 15 executions of the script, equivalent of clicking the link in the History column in SR/Fields
     * @return
     */
    @JsonIgnore
    ArrayList<ScriptFieldExecution> getExecutions() {

        HttpResponse<ArrayList<Map>> rawResponse = jim.rest.get("/rest/scriptrunner/latest/diagnostics/results?functionId=$fieldConfigurationSchemeId").asObject(new GenericType<ArrayList<Map>>() {
        })
        assert rawResponse.status == 200: "Error getting execution history for $this"
        ArrayList<Map> rawExecutions = rawResponse.body
        rawExecutions.each {
            it.payload = objectMapper.readValue(it.payload.toString(), new TypeReference<Map>() {})
        }
        ArrayList<ScriptFieldExecution> executions = objectMapper.convertValue(rawExecutions, new TypeReference<ArrayList<ScriptFieldExecution>>() {
        })


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

        @JsonIgnore
        private static Logger log = LoggerFactory.getLogger(ScriptFieldExecution.class)
        @JsonIgnore
        private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();


        @JsonSetter("payload")
        public void setPayload(Map payloadRaw) {
            payload = payloadRaw

            Matcher issueKeyMatcher = payload.issue =~ /(\w+-\d+)/
            if (issueKeyMatcher.size() != 1) {
                log.warn("Could not determine issueKeys for ScriptFieldExecution, field:" + payload.customField)
            } else {
                payload.issue = issueKeyMatcher[0][1]
            }

        }


        String getIssueKey() {
            return payload?.issue
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


}
