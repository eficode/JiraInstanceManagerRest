package com.eficode.atlassian.jiraInstanceManager.beans

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.GenericType
import kong.unirest.HttpResponse
import kong.unirest.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@JsonInclude(JsonInclude.Include.NON_NULL)

class CustomFieldBean {


    static Logger log = LoggerFactory.getLogger(CustomFieldBean.class)
    static final ObjectMapper objectMapper = new ObjectMapper()

    @JsonProperty("id")
    public String id;
    @JsonProperty("name")
    public String name;
    @JsonProperty("type")
    public String type;
    @JsonProperty("searcherKey")
    public String searcherKey;
    @JsonProperty("projectIds")
    public List<Integer> projectIds;
    @JsonProperty("issueTypeIds")
    public List<Object> issueTypeIds;
    @JsonProperty("self")
    public String self;
    @JsonProperty("numericId")
    public Integer numericId;
    @JsonProperty("isLocked")
    public Boolean isLocked;
    @JsonProperty("isManaged")
    public Boolean isManaged;
    @JsonProperty("isAllProjects")
    public Boolean isAllProjects;
    @JsonProperty("isTrusted")
    public Boolean isTrusted;
    @JsonProperty("projectsCount")
    public Integer projectsCount;
    @JsonProperty("screensCount")
    public Integer screensCount;
    @JsonProperty("lastValueUpdate")
    public Long lastValueUpdate;
    @JsonProperty("issuesWithValue")
    public Integer issuesWithValue;
    @JsonProperty("description")
    public String description;
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

    @JsonIgnore
    static ArrayList<CustomFieldBean> getCustomFields(JiraInstanceManagerRest jim) {

        //startAt parameter for this rest parameter appears broken (tested in 9.4.2), this paging does not work as expected
        //ArrayList<JsonNode> rawResponse =  jim.getJsonPages("/rest/api/2/customFields", [:], "values")

        HttpResponse<Map> rawResponse = jim.rest.get("/rest/api/2/customFields")
                .cookie(jim.acquireWebSudoCookies())
                .queryString("maxResults", 2000)
                .asObject(new GenericType<Map>() {})

        assert rawResponse.status == 200 : "Error getting customFields from REST API"

        assert rawResponse.body.total <= 2000 : "Current implementation of getCustomFields() cant handle more than 2000 fields"
        assert (rawResponse.body.total as long) == (rawResponse.body.values as List).size() : "Error getting all CustomFields"

        ArrayList<CustomFieldBean> beans = objectMapper.convertValue(rawResponse.body.values, new TypeReference<ArrayList<CustomFieldBean>>() {
        })

        return beans
    }

    @JsonIgnore
    String toJson() {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
    }

    @JsonIgnore
    String toString() {
        return name + " (${id})"
    }

}