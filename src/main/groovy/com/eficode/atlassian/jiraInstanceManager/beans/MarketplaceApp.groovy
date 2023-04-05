package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.HttpResponse
import kong.unirest.Unirest
import kong.unirest.UnirestInstance


import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import unirest.shaded.com.google.gson.annotations.SerializedName


@JsonInclude(JsonInclude.Include.NON_NULL)

class MarketplaceApp {


    static final UnirestInstance unirest = Unirest.spawnInstance()
    static final ObjectMapper objectMapper = new ObjectMapper()

    @JsonProperty("_links")
    public Map links
    @JsonProperty("_embedded")
    public Embedded embedded
    @JsonProperty("id")
    public String id
    @JsonProperty("name")
    public String name
    @JsonProperty("key")
    public String key
    @JsonProperty("tagLine")
    public String tagLine
    @JsonProperty("summary")
    public String summary
    @JsonProperty("status")
    public String status
    @JsonIgnore
    public Map<String, Object> additionalProperties = [:]


    String toString() {
        return name + " (${embedded.version})"
    }

    static ArrayList<MarketplaceApp> searchMarketplace(String text, Hosting hosting = Hosting.Any) {

        UnirestInstance mrktUnirest = Unirest.spawnInstance()
        mrktUnirest.config().defaultBaseUrl("https://marketplace.atlassian.com")

        HttpResponse response = mrktUnirest.get("/rest/2/addons").queryString([hosting: hosting.name().toLowerCase(), text: text, withVersion: true]).asObject(Map)
        ArrayList<Map> appsRaw = response.body?.get("_embedded")?.get("addons")

        String nextPageUrl = response?.body?.get("_links")?.get("next")?.find { it.type == "application/json" }?.href

        while (nextPageUrl) {
            response = mrktUnirest.get(nextPageUrl).asObject(Map)
            appsRaw += response.body?.get("_embedded")?.get("addons")
            nextPageUrl = response?.body?.get("_links")?.get("next")?.find { it.type == "application/json" }?.href as String
        }
        mrktUnirest.shutDown()

        ArrayList<MarketplaceApp> marketplaceApps = appsRaw.collect { MarketplaceApp.fromMap(it) }
        return marketplaceApps

    }



    Version getLatestVersion(Hosting hosting = Hosting.Any) {

        HttpResponse response = unirest.get("https://marketplace.atlassian.com/rest/2/addons/${key}/versions/latest").queryString([hosting: hosting.name().toLowerCase()]).asObject(Map)

        Version version = objectMapper.convertValue(response.body, Version)

        return version
    }

    /**
     * Get a specific version of Marketplace app
     * @param versionName ex latest, 3.0, etc
     * @param hosting
     * @return
     */
    Version getVersion(String versionName, Hosting hosting = Hosting.Any) {

        if (versionName == "latest") {
            return getLatestVersion(hosting)
        }

        HttpResponse response = unirest.get("https://marketplace.atlassian.com/rest/2/addons/${key}/versions/name/" + versionName).queryString([hosting: hosting.name().toLowerCase()]).asObject(Map)

        Version version = objectMapper.convertValue(response.body, Version)

        return version

    }

    ArrayList<Version> getVersions(int maxVersions = 10, Hosting hosting = Hosting.Any) {

        HttpResponse response = unirest.get("https://marketplace.atlassian.com/rest/2/addons/${key}/versions").queryString([hosting: hosting.name().toLowerCase()]).asObject(Map)

        ArrayList<Map> versionsRaw = response.body?.get("_embedded")?.get("versions")


        String nextPageUrl = response?.body?.get("_links")?.get("next")?.href

        while (nextPageUrl && versionsRaw.size() < maxVersions) {
            response = unirest.get("https://marketplace.atlassian.com" + nextPageUrl).asObject(Map)
            versionsRaw += response.body?.get("_embedded")?.get("versions")
            nextPageUrl = response?.body?.get("_links")?.get("next")?.href
        }

        if (versionsRaw.size() > maxVersions) {
            versionsRaw = versionsRaw.subList(0, maxVersions)
        }

        ArrayList<Version> versions = objectMapper.convertValue(versionsRaw, new TypeReference<ArrayList<Version>>() {})

        return versions

    }

    @JsonAnyGetter
    Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties
    }

    @JsonAnySetter
    void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value)
    }

    static MarketplaceApp fromMap(Map srcMap) {
        return objectMapper.convertValue(srcMap, MarketplaceApp)
    }

    enum Hosting {

        @SerializedName("any")
        Any,
        @SerializedName("cloud")
        Cloud,
        @SerializedName("datacenter")
        Datacenter,
        @SerializedName("server")
        Server
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class Embedded {

        @JsonProperty("applications")
        public ArrayList<Map> applications
        @JsonProperty("categories")
        public ArrayList<Map> categories
        @JsonProperty("distribution")
        public Map distribution
        @JsonProperty("logo")
        public Map logo
        @JsonProperty("reviews")
        public Map reviews
        @JsonProperty("vendor")
        public Map vendor
        @JsonProperty("version")
        public Version version
        @JsonProperty("lastModified")
        public String lastModified
        @JsonIgnore
        public Map<String, Object> additionalProperties = [:]


        @JsonAnyGetter
        Map<String, Object> getAdditionalProperties() {
            return this.additionalProperties
        }

        @JsonAnySetter
        void setAdditionalProperty(String name, Object value) {
            this.additionalProperties.put(name, value)
        }

    }


    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class Version {

        @JsonProperty("_links")
        public Map links;
        @JsonProperty("_embedded")
        public Map embedded;
        @JsonProperty("name")
        public String name;
        @JsonProperty("status")
        public String status;
        @JsonProperty("paymentModel")
        public String paymentModel;
        @JsonProperty("release")
        public Map release;
        @JsonProperty("static")
        public Boolean _static;
        @JsonProperty("deployable")
        public Boolean deployable;

        @JsonProperty("deployment")
        private Map deployment;
        public ArrayList<Hosting> hosting = []
        @JsonProperty("vendorLinks")
        public Map vendorLinks;
        @JsonIgnore
        public Map<String, Object> additionalProperties = [:]


        String toString() {
            return name + " ${hosting.collect { it.name() }.join(", ")}"
        }

        String getDownloadUrl() {
            return embedded?.artifact?._links?.binary?.href
        }

        @JsonAnyGetter
        Map<String, Object> getAdditionalProperties() {
            return this.additionalProperties
        }

        @JsonAnySetter
        void setAdditionalProperty(String name, Object value) {
            this.additionalProperties.put(name, value)
        }


        @JsonProperty("deployment")
        void setDeployment(Map<String, Object> deploymentMap) {

            Hosting.values().each { hostingType ->

                deploymentMap.findAll { it.key }
                if (deploymentMap.find { it.key.equalsIgnoreCase(hostingType.name()) }?.value == true) {
                    this.hosting.add(hostingType)
                }
            }
        }


    }
}