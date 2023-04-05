package com.eficode.atlassian.jiraInstanceManager.beans


import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper;


@JsonInclude(JsonInclude.Include.NON_NULL)
class JiraApp {

    @JsonProperty("enabled")
    public Boolean enabled;
    @JsonProperty("links")
    public Links links;
    @JsonProperty("name")
    public String name;
    @JsonProperty("version")
    public String version;
    @JsonProperty("userInstalled")
    public Boolean userInstalled;
    @JsonProperty("optional")
    public Boolean optional;
    @JsonProperty("static")
    public Boolean _static;
    @JsonProperty("unloadable")
    public Boolean unloadable;
    @JsonProperty("description")
    public String description;
    @JsonProperty("key")
    public String key;
    @JsonProperty("usesLicensing")
    public Boolean usesLicensing;
    @JsonProperty("remotable")
    public Boolean remotable;
    @JsonProperty("vendor")
    public Vendor vendor;
    @JsonProperty("applicationKey")
    public String applicationKey;
    @JsonProperty("applicationPluginType")
    public String applicationPluginType;
    @JsonIgnore
    public Map<String, Object> additionalProperties = [:]

    static final ObjectMapper objectMapper = new ObjectMapper()


    String toString() {
        return name + " ($version)"
    }

    MarketplaceApp getMarketplaceApp() {

        return MarketplaceApp.searchMarketplace(name, MarketplaceApp.Hosting.Any).find {it.key == key}

    }

    static JiraApp fromMap(Map srcMap) {
        objectMapper.convertValue(srcMap, JiraApp.class)
    }

    @JsonAnyGetter
    Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }


    @JsonInclude(JsonInclude.Include.NON_NULL)
    class Links {

        @JsonProperty("modify")
        public String modify;
        @JsonProperty("self")
        public String self;
        @JsonProperty("plugin-summary")
        public String pluginSummary;
        @JsonProperty("plugin-icon")
        public String pluginIcon;
        @JsonProperty("plugin-logo")
        public String pluginLogo;
        @JsonProperty("manage")
        public String manage;
        @JsonProperty("delete")
        public String delete;
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    class Vendor {

        @JsonProperty("name")
        public String name;
        @JsonProperty("marketplaceLink")
        public String marketplaceLink;
        @JsonProperty("link")
        public String link;
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