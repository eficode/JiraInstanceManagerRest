package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
    static Logger log = LoggerFactory.getLogger(UserRestBean.class)


    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>()

    @JsonSetter("groups")
    void setGroup(Map rawMap) {
        try {
            ArrayList<Map> items = rawMap.get("items", null) as ArrayList<Map>
            ArrayList<String> groupNames = items?.collect {it.get("name", null)}
            this.groups = groupNames
        }catch (Throwable ex) {
            log.error("Error parsing groups based on raw input:" + rawMap)
            throw ex
        }

    }

    @JsonSetter("applicationRoles")
    void setApplicationRoles(Map rawMap) {

        try {
            ArrayList<Map> items = rawMap.get("items", null) as ArrayList<Map>
            ArrayList<String> roleNames = items?.collect {it.get("key", null)}
            this.applicationRoles = roleNames
        }catch (Throwable ex) {
            log.error("Error parsing applicationRoles based on raw input:" + rawMap)
            throw ex
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


}
