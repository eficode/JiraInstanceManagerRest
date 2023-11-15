package com.eficode.atlassian.jiraInstanceManager.beans

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.HttpResponse

class ProjectBean {

    @JsonAlias(["self"])
    public String returnUrl
    @JsonAlias(["id"])
    public Integer projectId
    @JsonAlias(["key"])
    public String projectKey
    @JsonAlias(["name"])
    public String projectName
    public String projectTypeKey
    public Map remoteProjectLinks
    public Map<String, Object> unknownParameters = [:]

    static final ObjectMapper objectMapper = new ObjectMapper()

    @JsonIgnore()
    JiraInstanceManagerRest jiraInstance

    ProjectBean() {}

    static ProjectBean fromMap(Map rawMap, JiraInstanceManagerRest jim) {
        ProjectBean newBean = objectMapper.convertValue(rawMap, ProjectBean.class)
        newBean.jiraInstance = jim
        return newBean
    }


    @JsonAnyGetter
    Map<String, Object> getUnknownParameters() {
        return unknownParameters;
    }

    @JsonAnySetter
    void set(String name, Object value) {
        unknownParameters.put(name, value);

    }


    /**
     * Returns true if userName has the role roleName
     * @param userName
     * @param roleName
     * @return
     */
    boolean userHasProjectRole(String userName, String roleName) {

        ArrayList<Map<String, String>> rawActors = getProjectRoleActorsRaw(roleName)


        return rawActors.any { actorMap ->
            actorMap.name == userName && actorMap.type == "atlassian-user-role-actor"
        }

    }

    /**
     * Returns an array of role actors:
     * example: {
     "id": 10236,
     "displayName": "Mister Admin",
     "type": "atlassian-user-role-actor",
     "name": "admin",
     "avatarUrl": "https://www.gravatar.com/avatar/64e1b8d34f425d19e1ee2ea7236d3028?d=mm&s=16"
     },
     {
     "id": 10242,
     "displayName": "mr.fulltime.worker",
     "type": "atlassian-user-role-actor",
     "name": "mr.fulltime.worker",
     "avatarUrl": "https://www.gravatar.com/avatar/d41d8cd98f00b204e9800998ecf8427e?d=mm&s=16"
     }
     * @param roleName
     * @return
     */
    ArrayList<Map<String, String>> getProjectRoleActorsRaw(String roleName) {


        String roleUrl = getProjectRoleUrl(roleName)

        HttpResponse<Map<String, String>> actorsRawResponse = jiraInstance.unirest.get(roleUrl)
                .cookie(jiraInstance.acquireWebSudoCookies())
                .asObject(Map)

        assert actorsRawResponse.status == 200: "Error getting role details with URL:" + roleUrl

        ArrayList<Map<String, String>> outList = actorsRawResponse.body.get("actors") as ArrayList<Map<String, String>>

        return outList

    }

    /**
     * Returns a map where each key is the display name of a role and the value is a link useful for querying for more info
     * @return ex:
     * {
     "Service Desk Team": "http://jira.localhost:8080/rest/api/2/project/10000/role/10101",
     "Service Desk Customers": "http://jira.localhost:8080/rest/api/2/project/10000/role/10100",
     "Administrators": "http://jira.localhost:8080/rest/api/2/project/10000/role/10002",
     "Tempo Project Managers": "http://jira.localhost:8080/rest/api/2/project/10000/role/10102"}
     */
    Map<String, String> getProjectRolesRaw() {
        HttpResponse<Map<String, String>> roleNamesResponse = jiraInstance.unirest.get("/rest/api/2/project/$projectKey/role")
                .cookie(jiraInstance.acquireWebSudoCookies())
                .asObject(Map)

        assert roleNamesResponse.status == 200: "Error getting role names for project ${projectKey}"

        return roleNamesResponse.body
    }


    String getProjectRoleUrl(String roleName) {
        Map<String, String> projectRolesMap = projectRolesRaw
        String roleUrl = projectRolesMap.getOrDefault(roleName, "")

        assert roleUrl != "": "Error finding role ${roleName}, project has these roles:" + projectRolesMap.keySet().collect { "\"$it\"" }.join(", ")

        return roleUrl

    }

    boolean addUserToProjectRole(String userName, String roleName) {


        String roleUrl = getProjectRoleUrl(roleName)

        HttpResponse<Map<String, Object>> roleAddResponse = jiraInstance.unirest.post(roleUrl).contentType("application/json").cookie(jiraInstance.acquireWebSudoCookies()).body(["user": [userName]]).asObject(Map)

        assert roleAddResponse.status == 200: "Error adding $userName to $roleName using POST to url:" + roleUrl

        ArrayList<Map<String, String>> actors = roleAddResponse.body.actors as ArrayList<Map<String, String>>

        assert actors.any { userName in [it.displayName, it.name] }: "Could not verify that $userName was propperley added to role $roleName, current actors are:" + actors.toString()

        return true
    }
}