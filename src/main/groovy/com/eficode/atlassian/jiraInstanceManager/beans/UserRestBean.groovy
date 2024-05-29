package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter

class UserRestBean {

    public URI self
    public String key
    public String name
    public String emailAddress
    public Map avatarUrls
    public String displayName
    public Boolean active
    public Boolean deleted
    public String timeZone
    public String locale
    @JsonIgnore
    public List groups
    @JsonIgnore
    public List applicationRoles
    public String expand
    public String lastLoginTime


    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>()

    @JsonSetter("groups")
    void setGroup(Map rawMap) {
        this.groups = (rawMap.get("items") as Map)?.collect {it."name"}
    }

    @JsonSetter("applicationRoles")
    void setApplicationRoles(Map rawMap) {
        this.applicationRoles = (rawMap.get("items") as Map)?.collect {it."key"}

    }

    @JsonAnyGetter
    Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties
    }

    @JsonAnySetter
    void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value)
    }


}
