package com.eficode.atlassian.jiraInstanceManager.beans


import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.GenericType
import kong.unirest.HttpResponse
import kong.unirest.UnirestInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class FieldBean {

    static Logger log = LoggerFactory.getLogger(FieldBean.class)
    static final ObjectMapper objectMapper = new ObjectMapper()


    @JsonProperty("id")
    String id
    @JsonProperty("name")
    String name
    @JsonProperty("custom")
    Boolean custom
    @JsonProperty("orderable")
    Boolean orderable
    @JsonProperty("navigable")
    Boolean navigable
    @JsonProperty("searchable")
    Boolean searchable
    @JsonProperty("clauseNames")
    List<String> clauseNames
    @JsonProperty("schema")
    FieldSchema schema
    @JsonIgnore()
    JiraInstanceManagerRest jiraInstance
    @JsonIgnore
    Map<String, Object> additionalProperties = [:]


    static class FieldSchema {

        @JsonProperty("type")
        String type
        @JsonProperty("system")
        String system
        @JsonProperty("items")
        String items
        @JsonProperty("custom")
        public String custom;
        @JsonProperty("customId")
        public Integer customId;
        @JsonIgnore
        private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>()

        @JsonAnyGetter
        Map<String, Object> getAdditionalProperties() {
            return this.additionalProperties
        }

        @JsonAnySetter
        void setAdditionalProperty(String name, Object value) {
            this.additionalProperties.put(name, value)
        }

    }


    static class FieldType {

        @JsonProperty("name")
        public String name;
        @JsonProperty("description")
        public String description;
        @JsonProperty("key")
        public String key;
        @JsonProperty("previewImageUrl")
        public String previewImageUrl;
        @JsonProperty("categories")
        public List<String> categories;
        @JsonProperty("options")
        public Boolean options;
        @JsonProperty("cascading")
        public Boolean cascading;
        @JsonProperty("searchers")
        public List<String> searchers;
        @JsonProperty("isManaged")
        public Boolean isManaged;
        @JsonIgnore
        private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

        @JsonAnyGetter
        Map<String, Object> getAdditionalProperties() {
            return this.additionalProperties;
        }

        @JsonAnySetter
        public void setAdditionalProperty(String name, Object value) {
            this.additionalProperties.put(name, value);
        }


        static FieldType fromMap(Map rawMap) {
            FieldType fieldType = objectMapper.convertValue(rawMap, FieldType.class)

            return fieldType
        }


        static ArrayList<FieldType> getFieldTypes(JiraInstanceManagerRest jim) {

            ArrayList<FieldType> types = []

            try {
                HttpResponse<Map> rawResponse = jim.unirest.get("/rest/globalconfig/1/customfieldtypes")
                        .cookie(jim.acquireWebSudoCookies())
                        .asObject(new GenericType<Map>() {})


                types = rawResponse.body.types.collect { fromMap(it as Map) }
            } catch (ex) {


                log.error("Error getting JIRA Fields Type:" + ex.message)
                throw ex
            }


            return types

        }
    }


    @JsonAnyGetter
    Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties
    }

    @JsonAnySetter
    void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value)
    }


    String toString() {
        return name + " (${id}) - ${schema?.type?.capitalize()}" + (schema?.type == "array" ? "<${schema?.items}>" : "")
    }

    Long getNumericId() {
        return id.replace("customfield_", "").toLong()
    }

    static FieldBean fromMap(Map rawMap, JiraInstanceManagerRest jim) {
        FieldBean jiraFieldBean

        if (rawMap.custom && rawMap?.schema?.custom?.contains("com.riadalabs.jira.plugins.insight")) {
            jiraFieldBean = objectMapper.convertValue(rawMap, AssetFieldBean.class)
        } else {
            jiraFieldBean = objectMapper.convertValue(rawMap, FieldBean.class)
        }

        jiraFieldBean.jiraInstance = jim

        return jiraFieldBean
    }

    FieldType getFieldType(boolean useCache = true) {


        if (!custom) {
            FieldType systemType = new FieldType()
            systemType.with { [name: name, id: id] }
            return systemType
        } else {

            return jiraInstance.getFieldTypes(useCache).find { it.key == schema.custom }
        }

    }


    /**
     * Get projects that the field has been applied to
     * Returns all project if it´s a globally applied field
     * @param useCache use cached information if set ot true
     * @return
     */
    ArrayList<ProjectBean> getFieldProjects(boolean useCache = true) {

        if (!custom) {
            //Return all projects if its not a custom field
            jiraInstance.getProjects(useCache)
        } else {

            try {

                ArrayList<Map> rawResponse = jiraInstance.getJsonPages("/rest/api/2/customFields", [search: name], "values")
                Map rawField = rawResponse.find { it.id == id }
                assert !rawField.isEmpty(): "Error finding customfield $id"

                if (rawField.isAllProjects) {
                    //Return all projects if global
                    return jiraInstance.getProjects()
                }
                ArrayList<Double> fieldProjectIds = rawField.projectIds as ArrayList<Double>
                ArrayList<ProjectBean> projectBeans = jiraInstance.getProjects(useCache).findAll {
                    it.projectId.toDouble() in fieldProjectIds
                }

                return projectBeans
            } catch (ex) {


                log.error("Error getting projects for JIRA Field (${toString()}):" + ex.message)
                throw ex
            }

        }

    }


    /**
     * Get issue types that the field has been explicitly applied to
     * @param useCache use cached information if set ot true
     * @return
     */
    ArrayList<IssueTypeBean> getFieldIssueTypes(boolean useCache = true) {

        if (!custom) {
            //Return all projects if its not a custom field
            jiraInstance.getIssueTypes(useCache)
        } else {

            try {

                ArrayList<Map> rawResponse = jiraInstance.getJsonPages("/rest/api/2/customFields", [search: name], "values")
                Map rawField = rawResponse.find { it.id == id }
                assert !rawField.isEmpty(): "Error finding customfield $id"

                ArrayList<IssueTypeBean> instanceIssueTypes = jiraInstance.getIssueTypes(useCache)

                if (rawField.issueTypeIds == []) {
                    //Return all issue types if global
                    return instanceIssueTypes
                }
                ArrayList<String> fieldIssueTypeIds = rawField.issueTypeIds as ArrayList<String>
                ArrayList<IssueTypeBean> issueTypeBeans = instanceIssueTypes.findAll {
                    it.id in fieldIssueTypeIds
                }

                return issueTypeBeans
            } catch (ex) {


                log.error("Error getting projects for JIRA Field (${toString()}):" + ex.message)
                throw ex
            }

        }

    }


    /**
     * Get all fields (custom and system) in instance
     * @param jim The instance to fetch fields from
     * @return an array of JiraFieldBeans
     */
    static ArrayList<FieldBean> getFields(JiraInstanceManagerRest jim) {

        UnirestInstance unirest = jim.getUnirest()
        ArrayList<FieldBean> fields

        try {

            HttpResponse<ArrayList<Map>> rawResponse = unirest.get("/rest/api/2/field")
                    .cookie(jim.acquireWebSudoCookies())
                    .asObject(new GenericType<ArrayList<Map>>() {})

            fields = rawResponse.body.collect { fromMap(it, jim) }


        } catch (ex) {


            log.error("Error getting JIRA Fields:" + ex.message)
            throw ex
        }


        return fields

    }


    /**
     * Create a new JIRA Customfield field
     * @param jim The instance where the field should be created
     * @param name Name of the new field
     * @param description (Optional) Description of the new field
     * @param searcherKey The key of the searcher to use, can be found using getFieldTypesInInstance()
     * @param typeKey The key of the type to use, can be found using getFieldTypesInInstance()
     * @param projectIds The projects to apply to, set to [] for all
     * @param issueTypeIds The IDs to apply to, set to [-1] for all
     * @return a new FieldBean
     */
    static FieldBean createCustomfield(JiraInstanceManagerRest jim, String name, String searcherKey, String typeKey, String description = "", ArrayList<String> projectIds = [], ArrayList<String> issueTypeIds = ["-1"]) {

        UnirestInstance unirest = jim.getUnirest()

        try {
            HttpResponse<Map> rawResponse = unirest.post("/rest/api/2/field")
                    .cookie(jim.acquireWebSudoCookies())
                    .contentType("application/json")
                    .body([
                            name        : name,
                            description : description,
                            searcherKey : searcherKey,
                            type        : typeKey,
                            projectIds  : projectIds,
                            issueTypeIds: issueTypeIds

                    ])
                    .asObject(Map)

            assert rawResponse.status == 201: "API returned unexpected status code when creating field:" + rawResponse.status
            FieldBean newFieldBean = getFields(jim).find { it.id == rawResponse.body.id }

            return newFieldBean
        } catch (ex) {
            log.error("Error creating JIRA Field ($name):" + ex.message)
            throw ex
        }


    }

    boolean deleteCustomField() {
        assert custom: "Error, cant System field"
        return deleteCustomField(jiraInstance, id)
    }


    static boolean deleteCustomField(JiraInstanceManagerRest jim, String fieldId) {

        assert fieldId.startsWith("customfield_"): "fieldId should start with customfield_"

        try {
            HttpResponse<Map> rawResponse = jim.unirest.delete("/rest/api/2/customFields")
                    .cookie(jim.acquireWebSudoCookies())
                    .contentType("application/json")
                    .queryString(
                            [
                                    ids: fieldId
                            ]
                    )
                    .asObject(Map)

            assert rawResponse.status == 200: "API returned unexpected status code when deleting field:" + rawResponse.status
            assert rawResponse.body.deletedCustomFields == [fieldId]: "API returned unexpected body when deleting field:" + rawResponse.body

            return true
        } catch (ex) {
            log.error("Error deleting JIRA Field ($fieldId):" + ex.message)
            throw ex
        }

    }

}
