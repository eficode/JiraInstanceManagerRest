package com.eficode.atlassian.jiraInstanceManager.beans

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.core.UnirestInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IssueTypeBean {

    static Logger log = LoggerFactory.getLogger(FieldBean.class)
    static final ObjectMapper objectMapper = new ObjectMapper()


    @JsonProperty("self")
    public String self;
    @JsonProperty("id")
    public String id;
    @JsonProperty("description")
    public String description;
    @JsonProperty("iconUrl")
    public String iconUrl;
    @JsonProperty("name")
    public String name;
    @JsonProperty("subtask")
    public Boolean subtask;
    @JsonProperty("avatarId")
    public Integer avatarId;
    @JsonIgnore()
    JiraInstanceManagerRest jiraInstance
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    static IssueTypeBean fromMap(Map rawMap, JiraInstanceManagerRest jim) {
        IssueTypeBean issueTypeBean = objectMapper.convertValue(rawMap, IssueTypeBean.class)
        issueTypeBean.jiraInstance = jim
        return issueTypeBean
    }

    static ArrayList<IssueTypeBean> getIssueTypes(JiraInstanceManagerRest jim) {


        try {

            ArrayList<Map> rawResponse = jim.getJsonPages("/rest/api/2/issuetype/page", [:], "values")

            return rawResponse.collect { fromMap(it, jim) }

        } catch (ex) {


            log.error("Error getting JIRA Issue Types:" + ex.message)
            throw ex
        }


    }
}
