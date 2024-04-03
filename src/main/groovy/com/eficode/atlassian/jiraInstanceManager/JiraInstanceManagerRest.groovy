package com.eficode.atlassian.jiraInstanceManager

import com.eficode.atlassian.jiraInstanceManager.beans.AssetAutomationBean
import com.eficode.atlassian.jiraInstanceManager.beans.CustomFieldBean
import com.eficode.atlassian.jiraInstanceManager.beans.FieldBean
import com.eficode.atlassian.jiraInstanceManager.beans.IssueBean
import com.eficode.atlassian.jiraInstanceManager.beans.IssueTypeBean
import com.eficode.atlassian.jiraInstanceManager.beans.JiraApp
import com.eficode.atlassian.jiraInstanceManager.beans.MarketplaceApp
import com.eficode.atlassian.jiraInstanceManager.beans.ObjectSchemaBean
import com.eficode.atlassian.jiraInstanceManager.beans.ProjectBean
import com.eficode.atlassian.jiraInstanceManager.beans.ScriptFieldBean
import com.eficode.atlassian.jiraInstanceManager.beans.SpockResult
import com.eficode.atlassian.jiraInstanceManager.beans.SrJob
import net.lingala.zip4j.ZipFile
import groovy.io.FileType
import groovy.json.JsonSlurper
import kong.unirest.core.Cookie
import kong.unirest.core.Cookies
import kong.unirest.core.Empty
import kong.unirest.core.GenericType
import kong.unirest.core.GetRequest
import kong.unirest.core.HttpResponse
import kong.unirest.core.JsonNode
import kong.unirest.core.Unirest
import kong.unirest.core.UnirestException
import kong.unirest.core.UnirestInstance
import org.apache.groovy.json.internal.LazyMap
import java.nio.file.Path

//import org.codehaus.groovy.runtime.ResourceGroovyMethods
import java.nio.file.Paths
import org.slf4j.Logger
import org.slf4j.LoggerFactory


import java.nio.file.StandardCopyOption

final class JiraInstanceManagerRest {

    static Logger log = LoggerFactory.getLogger(JiraInstanceManagerRest.class)
    public UnirestInstance rest
    public String baseUrl
    Cookies cookies
    public String adminUsername = "admin"
    public String adminPassword = "admin"
    public boolean useSamlNoSso = false //Not tested
    private boolean verifySsl = true
    private String proxyhost
    private Integer proxyPort


    ArrayList<FieldBean.FieldType> cached_FieldTypes = []
    ArrayList<FieldBean> cached_FieldBeans = []
    ArrayList<CustomFieldBean> cached_CustomFieldBeans = []
    ArrayList<ProjectBean> cached_Projects = []
    ArrayList<IssueTypeBean> cached_IssueTypes = []


    /**
     * Setup JiraInstanceManagerRest with admin/admin as credentials.
     * @param BaseUrl ex: http://localhost:8080
     */
    JiraInstanceManagerRest(String BaseUrl) {
        baseUrl = BaseUrl
        rest = getUnirestInstance(true)

    }

    /**
     * Setup JiraInstanceManagerRest with custom credentials
     * @param baseUrl ex: http://localhost:8080
     * @param username
     * @param password
     */
    JiraInstanceManagerRest(String username, String password, String BaseUrl) {
        baseUrl = BaseUrl
        adminUsername = username
        adminPassword = password
        rest = getUnirestInstance(true)

    }


    void setProxy(String proxyUrl, int proxyPort) {

        this.proxyhost = proxyUrl
        this.proxyPort = proxyPort
        rest = getUnirestInstance()


    }

    void setVerifySsl(boolean verify) {
        verifySsl = verify
        rest = getUnirestInstance()
    }

    /** --- REST Backend --- **/

    static Cookies extractCookiesFromResponse(HttpResponse response, Cookies existingCookies = null) {

        if (existingCookies == null) {
            existingCookies = new Cookies()
        }

        response.cookies.each { newCookie ->
            existingCookies.removeAll { it.name == newCookie.name }
            existingCookies.add(newCookie)
        }


        return existingCookies

    }

    /**
     * Generates cookies that are valid for a different user
     * Relies on scriptRunners Switch User script
     * @param userKey
     * @return a Cookies object for that user
     */
    Cookies acquireUserCookies(String userKey) {

        log.info("Getting cookies for user:" + userKey)
        Cookies cookies = acquireWebSudoCookies()
        log.info("\tAquired admin cookies needed for user switch:" + cookies)
        log.info("\tTransforming admin cookies in to user cookies")


        HttpResponse switchUserResponse = rest.post("/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.jira.admin.SwitchUser")
                .body(["FIELD_USER_ID": userKey, "canned-script": "com.onresolve.scriptrunner.canned.jira.admin.SwitchUser"])
                .contentType("application/json")
                .cookie(cookies)
                .asJson()


        assert switchUserResponse.status == 200, "Error getting cookies for user " + userKey

        UnirestInstance verifyInstance = getUnirestInstance(false)

        Map verifyMap = verifyInstance.get("/rest/api/2/myself").cookie(cookies).asJson().getBody().object.toMap()
        assert verifyMap.get("key") == userKey

        log.info("\tTransform of admin to user cookies appears successfull")
        log.info("\tReturning user cookies:" + cookies)

        return cookies
    }

    /**
     * Gets WebSudo cookies using the adminUsername and adminPassword credentials
     * @return
     */
    Cookies acquireWebSudoCookies() {
        Map cookies = useSamlNoSso ? getCookiesFromRedirect("/secure/admin/WebSudoAuthenticate") : getCookiesFromRedirect("/login.jsp?nosso")


        Cookie xsrfCookie = cookies.cookies.find { it.name == "atlassian.xsrf.token" }
        assert xsrfCookie: "Could not get atlassian.xsrf.token-cookie needed for WebSudo"

        UnirestInstance unirestInstance = getUnirestInstance(false)
        unirestInstance.config().followRedirects(false)


        HttpResponse webSudoResponse = unirestInstance.post("/secure/admin/WebSudoAuthenticate.jspa")
                .cookie(cookies.cookies)
                .field("atl_token", xsrfCookie.value)
                .field("webSudoPassword", adminPassword).asEmpty()


        assert webSudoResponse.status == 302, "Error acquiring Web Sudo"
        Cookies sudoCookies = webSudoResponse.cookies
        return sudoCookies
    }

    static String resolveRedirectPath(HttpResponse response, String previousPath = null) {

        String newLocation = response.headers.getFirst("Location")
        if (!newLocation) {
            return null
        } else if (!newLocation.startsWith("/")) {
            newLocation = previousPath.substring(0, previousPath.lastIndexOf("/") + 1) + newLocation
        }

        return newLocation

    }

    /**
     *
     * @param subPath
     * @param returnOnlyKey If set, only the content of this JSON root key will be returned
     * @return
     */
    ArrayList<JsonNode> getJsonPages(String subPath, Map queryParams = [:], String returnOnlyKey = "") {

        return getJsonPages(rest, subPath, queryParams, returnOnlyKey)
    }


    static ArrayList<Map> getJsonPages(UnirestInstance unirest, String subPath, Map queryParams = [:], String returnOnlyKey = "") {


        int currentPageStart = 0
        int resultsPerPage = 0
        int expectedTotal = 1


        ArrayList responses = []

        while (responses.size() < expectedTotal) {


            HttpResponse<Map> rawResponse = unirest.get(subPath)
                    .accept("application/json")
                    .queryString("startAt", currentPageStart + resultsPerPage)
                    .queryString(queryParams).asObject(new GenericType<Map>() {})

            Map response = rawResponse.body

            currentPageStart = response.getOrDefault("startAt", -1) as int
            resultsPerPage = response.getOrDefault("maxResults", -1) as int
            expectedTotal = response.getOrDefault("total", -1) as int

            if (returnOnlyKey) {
                if (response.containsKey(returnOnlyKey)) {

                    responses += response.get(returnOnlyKey) as ArrayList<Map>
                } else {

                    throw new InputMismatchException("Unexpected body returned from $subPath, expected JSON with \"$returnOnlyKey\"-node but got nodes: " + response.keySet().join(", "))
                }

            } else {
                responses += response.body
            }


        }
        return responses


    }

    /**
     * This method collects all cooikies supleid during a getRequest even if several 302 redirects are done
     * @param path
     * @return
     */
    Map getCookiesFromRedirect(String path, String username = adminUsername, String password = adminPassword, Map headers = [:]) {

        UnirestInstance unirestInstance = getUnirestInstance(false)
        unirestInstance.config().followRedirects(false)

        Cookies cookies = new Cookies()
        GetRequest getRequest = unirestInstance.get(path).headers(headers)
        if (username && password) {
            getRequest.basicAuth(username, password)
        }


        HttpResponse getResponse = getRequest.asString()
        cookies = extractCookiesFromResponse(getResponse, cookies)

        String newLocation = getResponse.headers.getFirst("Location")

        while (getResponse.status == 302) {


            newLocation = resolveRedirectPath(getResponse, newLocation)
            getResponse = unirestInstance.get(newLocation).asString()
            cookies = extractCookiesFromResponse(getResponse, cookies)

        }


        return ["cookies": cookies, "lastResponse": getResponse]
    }

    /** --- System Settings & Actions --- ***/

    /**
     * This is the equivalent of using "System/Mark Logs".
     * The main system logs will be marked with a an optional custom message and
     * also optionally rolled oved
     * @param markMessage An optional message mark the logs with
     * @param logRollover (default false) if set to true, new logfiles will be created and then marked
     * @return true on success
     */
    boolean markLogs(String markMessage = "", boolean logRollover = false) {


        HttpResponse<Empty> response = rest.post("/secure/admin/ViewLogging!markLogs.jspa")
                .header("X-Atlassian-Token", "no-check")
                .field("markMessage", markMessage)
                .field("rollOver", logRollover.toString())
                .field("mark", "Mark").asEmpty()

        String location = response.getHeaders().get("Location").find { true }
        return response.status == 302 && location == "ViewLogging.jspa"

    }


    /** --- Insight --- **/

    /**
     * Returns Array of insight schemas
     * @return
     */
    ArrayList<ObjectSchemaBean> getInsightSchemas() {

        log.info("Getting Insight Schemas")

        Cookies cookies = acquireWebSudoCookies()
        ArrayList<Map> rawMap = rest.get("/rest/insight/1.0/objectschema/list").cookie(cookies).asJson().body.getObject().toMap().objectschemas as ArrayList<Map>
        ArrayList<ObjectSchemaBean> schemaBeans = rawMap.collect { ObjectSchemaBean.fromMap(it) }

        return schemaBeans

    }

    /**
     * Export an Insight Schema to: JIRA_HOME/export/insight/$outputFileName.zip
     * @param schemaName Display name of schema
     * @param schemaId ID of schema (optional)
     * @param outputFileName Name of file
     * @param includeObjects Should the objects in the schema be included
     * @return Raw map of data returned from Insight API, some important data in map:
     *      map.status -> FINISHED
     *      map.result -> OK
     *      map.resultData.exportFileName -> $outputFileName.zip
     */
    Map exportInsightSchema(String schemaName, String schemaId = null, String outputFileName, boolean includeObjects) {

        log.info("Exporting Insight schema $schemaName ($schemaId)")
        Cookies cookies = acquireWebSudoCookies()

        if (!schemaId) {
            log.info("\tNo schemaId was provided, quering for it now")
            schemaId = getInsightSchemas().find { it.name == schemaName }?.id

            log.info("\t\tResolved Schema ID to:" + schemaId)

            assert schemaId: "Could not find Schema ID for Insight Schema:" + schemaName
        }

        outputFileName = outputFileName.endsWith(".zip") ? outputFileName : outputFileName + ".zip"

        Map bodyMap = [
                "fileName"            : outputFileName,
                "outputFileName"      : outputFileName,
                "objectSchemaId"      : schemaId,
                "totalObjectsInExport": 0,
                "includeObjects"      : includeObjects,
                "objectSchemaName"    : schemaName,

        ]

        Map resultMap = rest.post("/rest/insight/1.0/objectschemaexport/export/server")
                .cookie(cookies)
                .contentType("application/json")
                .body(bodyMap)
                .asJson().body.getObject().toMap()

        log.trace("\tExport finished with result:" + resultMap)

        log.info("\tExport file created:" + resultMap?.resultData?.exportFileName as String)
        return resultMap
    }

    /**
     * Import an insight schema from a previously exported ZIP file
     * @param fileName Name of file to be imported, already placed in $JIRA_HOME/import/insight/
     * @param schemaName Name of new scheme
     * @param schemaKey Key of new Scheme
     * @param schemaDescription Description of new Scheme
     * @param includeObjects Should the objects in the ZIP file also be imported?
     * @param importAttachments Should the Attachments in the ZIP file also be imported?
     * @param importObjectAvatars Should the Avatars in the ZIP file also be imported?
     * @return Raw map of data returned from Insight API, some important data in map:
     *      map.status -> FINISHED
     *      map.result -> OK
     *      map.resultMessage -> Imported object schema with objects (if any) successfully.
     */
    Map importInsightSchema(String fileName, String schemaName, String schemaKey, String schemaDescription = "", boolean includeObjects = false, boolean importAttachments = false, boolean importObjectAvatars = false) {

        //fileName: as it appears in the gui dialog box in gui

        Map bodyMap = [
                "fileName"               : fileName,
                "objectSchemaName"       : schemaName,
                "objectSchemaKey"        : schemaKey,
                "objectSchemaDescription": schemaDescription,
                "includeObjects"         : includeObjects,
                "importAttachments"      : importAttachments,
                "importObjectAvatars"    : importObjectAvatars
        ]

        Cookies sudoCookies = acquireWebSudoCookies()

        HttpResponse validateResponse = rest.post("/rest/insight/1.0/objectschemaimport/import/server/nowarning").cookie(sudoCookies).body(bodyMap).header("Content-Type", "application/json").asJson()

        assert validateResponse.status == 200, "Error validating Import Schema parameters"

        HttpResponse importResponse = rest.post("/rest/insight/1.0/objectschemaimport/import/server").cookie(sudoCookies).body(bodyMap).header("Content-Type", "application/json").asJson()

        assert importResponse.status == 200, "Error starting import of Insight schema $fileName, :" + importResponse.body.toPrettyString()

        long schemaId = importResponse.body.getObject().get("resultData").objectSchemaId


        Map importProgress = importResponse.body.getObject().toMap()

        while (!importProgress.containsKey("progressInPercent") || importProgress.progressInPercent != 100) {

            HttpResponse progressResponse = rest.get("/rest/insight/1.0/progress/category/importobjectschema/" + schemaId).cookie(sudoCookies).asJson()
            importProgress = progressResponse.body.getObject().toMap()
            log.info("\tSchema import progress:" + importProgress.get("progressInPercent"))
            sleep(1000)

        }

        log.info("\tInsight schema import completed with status:" + importProgress.status)

        return importProgress

    }


    boolean deleteInsightSchema(int schemaId) {

        log.info("Deleting Insight Schema:" + schemaId)
        cookies = acquireWebSudoCookies()
        Map resultMap = rest.delete("/rest/insight/1.0/objectschema/" + schemaId)
                .cookie(cookies)
                .asJson().body.getObject().toMap()

        log.trace("\tAPI returned:" + resultMap)
        log.info("\tDelete status:" + resultMap?.status)
        return resultMap?.status == "Ok"
    }

    /**
     * This will create a sample JSM project using the "Insight IT Service Management" template
     * An associated Insight Schema will also be created.
     * The project will contain issues, and the schema will contain objects
     * @param name Name of the new project
     * @param key Key of the new project
     * @return A map containing information about the Project and Schema
     */

    Map createInsightProjectWithSampleData(String name, String key) {

        ArrayList<Integer> preExistingSchemaIds = getInsightSchemas().id
        ProjectBean projectBean = createDemoProject(name, key, "rlabs-project-template-itsm-demodata")
        ObjectSchemaBean schemaBean = getInsightSchemas().find { !preExistingSchemaIds.contains(it.id) }

        return [project: projectBean, schema: schemaBean]

    }


    /**
     * Creates one of the template schemas, all but "empty" has objectTypes.
     * None of the templates comes with objects
     * @param name Schema name
     * @param key Schema key
     * @param template hr, empty, itassets, crm
     * @return
     */
    ObjectSchemaBean createTemplateSchema(String name, String key, String template) {

        assert ["hr", "empty", "itassets", "crm"].contains(template): "Unknown template type $template, valid choices are: hr, empty, itassets, crm"

        log.info("\tWill create a new template schema")
        log.debug("\t\tSchema name:" + name)
        log.debug("\t\tSchema key:" + key)
        log.debug("\t\tSchema template:" + template)

        Map sampleSchemaMap = rest.post("/rest/insight/1.0/objectschemaimport/template")
                .cookie(sudoCookies)
                .contentType("application/json")
                .body([
                        "status"         : "ok",
                        "name"           : name,
                        "objectSchemaKey": key,
                        "type"           : template

                ]).asJson().body.getObject().toMap()

        log.info("\tSchema created with status ${sampleSchemaMap.status} and Id: " + sampleSchemaMap.id)
        assert sampleSchemaMap.status == "Ok", "Error creating sample schema:" + sampleSchemaMap?.errors?.values()?.join(",")


        return ObjectSchemaBean.fromMap(sampleSchemaMap)
    }


    /**
     * Creates a new Automation
     * When: Object Updated
     * If: $conditionAql matches
     * Then: Execute groovy script scriptFilePath
     * @param name
     * @param actorKey
     * @param conditionAql
     * @param scriptFilePath An absolut path
     * @param schemaId
     * @return A raw map representing the automation
     */
    AssetAutomationBean createScriptedObjectUpdatedAutomation(String name, String actorKey, String conditionAql, String scriptFilePath, String schemaId) {

        return createInsightAutomation(
                name,
                actorKey,
                "Object updated",
                "InsightObjectUpdatedEvent",
                null,
                null,
                conditionAql,
                "Execute Groovy script",
                AssetAutomationBean.ActionType.GroovyScript.type,
                "{\"absFilePath\":\"${scriptFilePath}\"}",
                schemaId)

    }

    AssetAutomationBean createInsightAutomation(String name, String actorUserKey, String eventName, String eventTypeId, String eventIql = null, String eventCron = null, String conditionIql, String actionName, String actionTypeId, String actionData, String schemaId) {


        LazyMap postBody = [
                id                  : null,
                name                : name,
                description         : null,
                schemaId            : schemaId,
                actorUserKey        : actorUserKey,
                disabled            : null,
                events              : [
                        [
                                id    : null,
                                name  : eventName,
                                typeId: eventTypeId,
                                iql   : eventIql,
                                cron  : eventCron
                        ]
                ],
                conditionsAndActions: [
                        [
                                id        : null,
                                name      : null,
                                conditions: [
                                        [
                                                id       : null,
                                                name     : conditionIql,
                                                condition: conditionIql
                                        ]
                                ],
                                actions   : [
                                        [
                                                id                   : null,
                                                name                 : actionName,
                                                typeId               : actionTypeId,
                                                data                 : actionData,
                                                minTimeBetweenActions: null
                                        ]
                                ]
                        ]
                ],
                created             : null,
                updated             : null,
                lastTimeOfAction    : null
        ]

        Cookies cookies = acquireWebSudoCookies()
        HttpResponse<AssetAutomationBean> response = rest.post("/rest/insight/1.0/automation/rule").cookie(cookies).header("Content-Type", "application/json").body(postBody).asObject(AssetAutomationBean.class)
        if(response.status != 200) {
            log.error("Sent body: ${postBody}")
            log.error("Error creating Asset Automation: ${response.status} - ${response.mapError(String.class)}")
        }
        assert response.status == 200: "Error creationg Asset Automation"


        return response.body

    }


    /** --- Marketplace --- **/


    /**
     * Search marketplace for Apps
     * @param text Text to match
     * @param hosting What type of hosting to look for, MarketplaceApp.Hosting.Any is default
     * @return List of matching apps
     */
    static ArrayList<MarketplaceApp> searchMarketplace(String text, MarketplaceApp.Hosting hosting = MarketplaceApp.Hosting.Any) {

        return MarketplaceApp.searchMarketplace(text, hosting)

    }

    /**
     * A helper method for installing ScriptRunner DataCenter
     * If SR is already installed but of a different version, it will be uninstalled and replaced with the
     * correct version
     * @param licence License key for ScriptRunner to use
     * @param versionNr The versions to use, defaults to "latest"
     * @return The JiraApp representation of the installed SR
     */
    JiraApp installScriptRunner(String licence, String versionNr = "latest") {
        return MarketplaceApp.installScriptRunner(this, licence, versionNr)
    }


    /** --- App management --- **/


    /**
     * Get list of installed apps
     * @return
     */
    ArrayList<JiraApp> getInstalledApps() {

        Cookies sudoCookies = acquireWebSudoCookies()
        HttpResponse pluginsResponse = rest.get("/rest/plugins/1.0/").cookie(sudoCookies).asObject(new GenericType<Map>() {
        })


        ArrayList<JiraApp> apps = pluginsResponse.body.plugins.collect { JiraApp.fromMap(it as Map) }

        return apps

    }

    /**
     * Uninstall an app
     * @param app JiraApp, retrieved with getInstalledApps
     * @return
     */
    boolean uninstallApp(JiraApp app) {

        assert app?.links?.delete: app.name + " does not have a delete link, unsure how to delete"
        Cookies sudoCookies = acquireWebSudoCookies()

        HttpResponse deleteResponse = rest.delete(app.links.delete).asEmpty()

        return deleteResponse.status == 204

    }


    boolean installApp(MarketplaceApp marketplaceApp, MarketplaceApp.Hosting hosting = MarketplaceApp.Hosting.Datacenter, String version = "latest", String license = null) {

        MarketplaceApp.Version versionToInstall = marketplaceApp.getVersion(version, hosting)

        return installApp(versionToInstall.getDownloadUrl(), license)

    }

    /**
     * Install a specific version of a MarketplaceApp
     * @param version A MarketplaceApp.Version object
     * @param license (Optional) A license key for the app
     * @return true on success
     */
    boolean installApp(MarketplaceApp.Version version, String license = null) {
        return installApp(version.getDownloadUrl(), license)
    }

    /**
     * Install an App from Marketplace
     * @param appUrl Can be obtained by going to the marketplace listing of the app, checking its versions and getting the "Download" link URL
     *      Ex: https://marketplace.atlassian.com/download/apps/123/version/456
     * @param license (Optional) A license key for the app
     * @return a bool representing success
     */
    boolean installApp(String appUrl, String license = null) {


        log.info("Installing App from URL:" + appUrl)
        Cookies sudoCookies = acquireWebSudoCookies()

        HttpResponse upmTokenResponse = rest.get("/rest/plugins/1.0/?").cookie(sudoCookies).asEmpty()
        String upmToken = upmTokenResponse.headers.getFirst("upm-token")

        HttpResponse installResponse = rest.post("/rest/plugins/1.0/?token=$upmToken").header("Content-Type", "application/vnd.atl.plugins.install.uri+json").body(["pluginUri": appUrl]).cookie(sudoCookies).asJson()

        Map installMap = installResponse.body.getObject().toMap()

        assert installMap.status.statusCode == 200, "Error Installing app: " + appUrl

        Map progress = [:]

        while (!progress.containsKey("done") || !progress.done) {

            HttpResponse taskProgress = rest.get(installMap.links.alternate).cookie(sudoCookies).asJson()

            progress = taskProgress.body.getObject().toMap()

            log.info("\tInstall Progress:" + progress.progress + ", Done:" + progress.done)
            sleep(1000)
        }

        assert progress.done, "Error Installing app: " + appUrl

        log.info("\tFinished installing $appUrl")


        if (license) {
            log.info("\tInstalling App license")
            String localAppUrl = progress.links.result

            String newLicense = license.replaceAll("[\n\r]", "")

            HttpResponse currentLicenseResponse = rest.get(localAppUrl + "/license").cookie(sudoCookies).asJson()
            Map currentLicenseMap = currentLicenseResponse.body.getObject().toMap()
            String currentLicense = currentLicenseMap.rawLicense?.replaceAll("[\n\r]", "")

            if (currentLicense == newLicense) {
                log.info("\t\tThe license is already installed")
            } else {
                HttpResponse putLicenseResponse = rest.put(localAppUrl + "/license").contentType("application/vnd.atl.plugins+json").cookie(sudoCookies).body(["rawLicense": newLicense]).asJson()

                Map putLicenseResponseMap = putLicenseResponse.body.getObject().toMap()

                assert putLicenseResponse.status == 200 && putLicenseResponseMap.valid, "Error updating application license"

                log.info("\t\tThe license was successfully installed")

            }


        }


        return true

    }


    boolean scriptRunnerIsInstalled() {
        log.info("Checking if ScriptRunner is installed and enabled")

        if (installedApps.find { it.key == "com.onresolve.jira.groovy.groovyrunner" }?.enabled) {
            log.info("\tScriptRunner is installed")
            return true
        } else {
            log.info("\tScriptRunner is not installed")
            return false
        }


    }


    /** --- JIRA Setup --- **/

    /**
     * This is only intended to be run just when the database has been setup for
     * an otherwise blank new JIRA instance.
     * Other than the supplied parameters this will also be setup:
     *
     *  <li> Access Mode: Private </li>
     *  <li> An admin account using adminUsername and adminPassword </li>
     *  <li> Disable email </li>
     *
     * @param jiraLicense JIRA application license to install
     * @param appTitle Title of JIRA instance
     * @param baseUrl Base url of instance
     * @return true on success
     */
    boolean setApplicationProperties(String jiraLicense, String appTitle = "Jira", String baseUrl = this.baseUrl) {

        log.info("Setting up JIRA Application Properties")
        Map redirectResponse = getCookiesFromRedirect("/")
        cookies = redirectResponse.cookies

        UnirestInstance localUnirest = getUnirestInstance(false)
        localUnirest.config().defaultBaseUrl(baseUrl).followRedirects(false)
        String setupAppPropertiesUrl = "/secure/SetupApplicationProperties.jspa"

        Integer failedRequests = 0
        Integer maxFailedAttempts = 3

        HttpResponse setAppProperties = null
        while (failedRequests < maxFailedAttempts ) {


            try {
                setAppProperties = localUnirest.post(setupAppPropertiesUrl)
                        .cookie(cookies)
                        .field("atl_token", cookies.find { it.name == "atlassian.xsrf.token" }.value)
                        .field("title", appTitle)
                        .field("mode", "private")
                        .field("baseURL", baseUrl)
                        .field("nextStep", "true")
                        .asString()
                assert setAppProperties.status == 302, "Error setting Application properties"
                break
            } catch (Throwable ignored) {
                failedRequests++
                log.warn("Error setting JIRA application properties, attempt nr ${failedRequests}")
                sleep(5000)
            }
        }

        assert setAppProperties.status == 302, "Error setting Application properties"
        log.info("\t\tSet title, mode and baseUrl successfully")


        log.info("Setting JIRA license, this will take a few minutes")


        String setLicenseUrl = "/secure/SetupLicense.jspa"

        failedRequests = 0

        HttpResponse setupLicenceResponse = null
        while (failedRequests < maxFailedAttempts) {

            try {
                setupLicenceResponse = localUnirest.post(setLicenseUrl)
                        .cookie(cookies)
                        .field("setupLicenseKey", jiraLicense.replaceAll("[\n\r]", ""))
                        .field("atl_token", cookies.find { it.name == "atlassian.xsrf.token" }.value)
                        .connectTimeout(4 * 60000)
                        .asJson()

                assert setupLicenceResponse.status == 302, "Error setting license"
                break
            } catch (Throwable ignored) {
                failedRequests++
                log.warn("Error setting JIRA License, attempt nr ${failedRequests}")
                sleep(5000)
            }
        }


        assert setupLicenceResponse.status == 302, "Error setting license"
        log.info("\t\tSet license successfully")

        log.info("\tSetting up admin account")
        String setupAdminUrl = "/secure/SetupAdminAccount.jspa"


        HttpResponse setupAdminResponse = localUnirest.post(setupAdminUrl)
                .cookie(cookies)
                .field("fullname", "Mister Admin")
                .field("email", "admin@admin.com")
                .field("username", adminUsername)
                .field("password", adminPassword)
                .field("confirm", adminPassword)
                .field("atl_token", cookies.find { it.name == "atlassian.xsrf.token" }.value)
                .asString()

        assert setupAdminResponse.status == 200, "Error setting up admin account"
        log.info("\t\tSet admin account successfully")


        log.info("\tSetting up email")
        String setupEmailUrl = "/secure/SetupMailNotifications.jspa"


        HttpResponse setupEmailResponse = localUnirest.post(setupEmailUrl)
                .cookie(cookies)
                .field("noemail", "true")
                .field("atl_token", cookies.find { it.name == "atlassian.xsrf.token" }.value)
                .asString()

        assert setupEmailResponse.status == 302, "Error setting up email"
        log.info("\t\tSet email successfully")


        return true

    }


    /**
     * This intended to run right as JIRA starts up the first time ever,
     * it will wait for JIRA to be responsive and will then configure JIRA to use a local H2 Database
     * @return true on success
     */
    boolean setupH2Database() {

        log.info("Setting up a blank H2 database for JIRA")
        long startTime = System.currentTimeMillis()
        UnirestInstance localUnirest = getUnirestInstance(false)


        Cookie xsrfCookie = null

        while (startTime + (3 * 60000) > System.currentTimeMillis()) {
            try {
                HttpResponse<String> response = localUnirest.get("/").asString()

                Cookie tempCookie = response.cookies.find { it.name == "atlassian.xsrf.token" }

                if (tempCookie != null) {
                    xsrfCookie = tempCookie
                }

                if (response.body.contains("jira-setup-mode")) {
                    log.info("\tJira has started and the Setup dialog has appeared")
                    break
                } else {
                    log.info("\tJira has started but the Setup dialog has not appeared yet")
                    sleep(1000)
                }

            } catch (UnirestException ex) {
                log.info("---- Jira not available yet ----")
                sleep(1000)
            }
        }

        if (System.currentTimeMillis() > startTime + 180000) {
            throw new SocketTimeoutException("Timeout waiting for JIRA Setup dialog")
        }

        log.info("Setting up local H2 database, this will take a several minutes.")
        HttpResponse setupDbResponse = null
        try {
            setupDbResponse = localUnirest.post("/secure/SetupDatabase.jspa")
                    .field("databaseOption", "internal")
                    .field("atl_token", xsrfCookie.value)
                    .connectTimeout((8 * 60000))
                    .asString()
        } catch (Throwable ex) {

            log.warn("\tJIRA returned unexpected response (${setupDbResponse?.status}) during H2DB setup, waiting to se if JIRA becomes responsive")
            if (setupDbResponse?.body) {
                log.warn("\t" * 2 + setupDbResponse?.body?.toString()?.take(15) + "...")
            }

            assert waitForJiraToBeResponsive(4 * 60): "Timed out waiting for JIRA to become responsive after H2DB setup"

        }


        assert localUnirest.get("/secure/SetupApplicationProperties!default.jspa").asEmpty().status == 200: "Error setting up H2DB database"

        log.info("\tLocal database setup")
        return true


    }


    /**
     * Remove annoying setup steps and pop-ups common when a new environment is setup
     * Intended to be run after setApplicationProperties() but can be run when a new user is created as well
     * @param userIsAdmin If true and if user has enough permissions admin pop-ups/warnings will also be removed
     * @return true on success
     */
    boolean setupUserBasicPref(boolean userIsAdmin = true) {

        try {


            log.info("Setting basic user preferences, and removing annoying popups")
            HttpResponse<Map> languageResponse = rest.delete("/rest/api/2/mypreferences").queryString("key", "jira.user.locale").body(-1).contentType("application/json").asObject(Map)
            assert languageResponse.status == 404 && languageResponse.body.get("errorMessages").toString().contains("key not found")
            log.debug("\tSet user language to english")

            if (userIsAdmin) {
                assert rest.post("/rest/onboarding/1.0/flow/cyoaFirstUseFlow/complete").asEmpty().status == 200: "Error skipping project import"
                assert rest.get("/secure/JIMOnboardingPage.jspa").asEmpty().status == 200: "Error skipping project import"
                assert rest.get("/secure/Dashboard.jspa").asEmpty().status == 200: "Error skipping project import"
                log.debug("\tSkipped import of projects")

            }


            assert rest.put("/rest/flags/1.0/flags/com.atlassian.jira.tzdetect.3600000%2C7200000/dismiss").asEmpty().status == 204: "Error Setting user timezone to default"
            log.debug("\tSet default timezone")


            if (userIsAdmin) {
                assert rest.post("/rest/troubleshooting/1.0/dismissNotification").contentType("application/json").body(["notificationId": "1", "snooze": true, "username": adminUsername]).asEmpty().status == 204: "Error removing H2 DB warning"
                log.debug("\tRemoved warning about using local H2 database")

            }


            assert rest.post("/rest/helptips/1.0/tips").contentType("application/json").body(["id": "qs-onboarding-tip"]).asEmpty().status == 204: "Error removing search suggestion"
            log.debug("\tRemoved popup information about JQL")

            assert rest.put("/rest/flags/1.0/flags/com.atlassian.jira.reindex.required/dismiss").asEmpty().status == 204: "Error Re-Index suggestion"
            log.debug("\tRemoved popup suggesting a reindex")
        } catch (Throwable tr) {
            log.warn("There where errors setting basic user preferences:" + tr.message)
            return false
        }

        return true
    }


    /**
     * Waits for JIRAs REST-endpoint /status to return RUNNING
     * Note that Apps and other system funcntioanlity might not be ready yet.
     * Consider using waitForSrToBeResponsive() if your JIRA has ScriptRunner
     * @param timeOutS If this timeout is breached false will be returned
     * @return true if JIRA got responsive before timeout was reached
     */
    boolean waitForJiraToBeResponsive(long timeOutS = 160) {


        HttpResponse<Map> response = null

        log.info("\tWaiting for JIRA to become responsive")
        UnirestInstance unirest = getUnirestInstance(false)
        long start = System.currentTimeSeconds()
        while (response == null || !(response.body?.get("state")?.toString() in ["RUNNING", "FIRST_RUN"])) {

            try {


                response = unirest.get("/status").asObject(Map.class).ifFailure {
                    log.warn("JIRA not yet responsive")
                    sleep(2000)
                }

                if ((start + timeOutS) < System.currentTimeSeconds()) {
                    log.error("Timed out waiting for JIRA to start after ${System.currentTimeSeconds() - start} seconds")
                    return false
                }

            } catch (Throwable ignored) {
                sleep(2000)
            }


        }

        log.debug("\t\tJIRA Status started reporting ready after after ${System.currentTimeSeconds() - start} seconds")


        HttpResponse<String> guiResponse = null
        while (guiResponse == null || guiResponse.status >= 400 || !guiResponse.body) {

            try {


                guiResponse = unirest.get("/").asString().ifFailure {
                    log.warn("JIRA returned status ${response.body?.get("state")} but GUI is not yet responsive")
                    sleep(2000)
                }

                if ((start + timeOutS) < System.currentTimeSeconds()) {
                    log.error("Timed out waiting for JIRA GUI to start after ${System.currentTimeSeconds() - start} seconds")
                    return false
                }
            } catch (Throwable ignored) {
                sleep(2000)
            }


        }


        log.debug("\t\tJIRA started after ${System.currentTimeSeconds() - start} seconds")
        return response.body.get("state").toString() in ["RUNNING", "FIRST_RUN"] && guiResponse.status < 400
    }

    /**
     * Waits for ScriptRunner to become responsive, a good way to determine that JIRA is ready after starting up
     * @param timeOutS If this timeout is breached false will be returned
     * @return true if SR got responsive before timeout was reached
     */
    boolean waitForSrToBeResponsive(long timeOutS = 90) {


        log.info("Waiting for ScriptRunner to become responsive")
        long start = System.currentTimeSeconds()

        boolean srResponsive = false
        while (!srResponsive) {


            try {

                Map rawResponse = executeLocalScriptFile("return true")
                srResponsive = rawResponse.success

                if (!srResponsive) {
                    log.warn("\tSR not yet responsive")
                    sleep(2000)
                }

            } catch (Throwable ignored) {
                log.warn("\tSR not yet responsive")
                sleep(2000)
            }

            if ((start + timeOutS) < System.currentTimeSeconds()) {
                log.error("\tTimed out waiting for JSM to start after ${System.currentTimeSeconds() - start} seconds")
                return false
            }

        }
        log.debug("\t\tSR started after ${System.currentTimeSeconds() - start} seconds")

        return srResponsive

    }

    /** --- Project CRUD --- **/

    String getAvailableProjectKey(String prefix) {

        prefix = prefix.toUpperCase()

        ArrayList<ProjectBean> existingProjects = projects.findAll { it.projectKey.startsWith(prefix) }

        for (int i = 1; i <= 100; i++) {

            if (!existingProjects.projectKey.contains(prefix + i)) {
                return (prefix + i.toString())
            }
        }
        throw new Exception("Could not find available project key with prefix:" + prefix)

    }


    /**
     * Get ID of a JSM projects portal
     * @param projectKey
     * @return
     */
    Integer getJsmPortalId(String projectKey) {

        Map rawResponse = rest.get("/rest/servicedeskapi/portals/project/$projectKey").cookie(acquireWebSudoCookies()).asObject(Map).body


        return rawResponse.getOrDefault("id", null) as Integer

    }

    /**
     * Get reuqest types available in a JSM portal
     * @param portalId Id of the portal
     * @param limit Nr of requests to fetch, max 100, default 50
     * @return A map where each key is a request name and the value is the id, ex: [Get a guest wifi account : 3]
     */
    Map<String, Integer> getPortalRequestTypes(Integer portalId, Integer limit = 50) {

        assert limit <= 100: "Can request maximum 100 request types"
        //JSM pageination works different than the rest of JIRA

        Map rawResponse = rest.get("/rest/servicedeskapi/servicedesk/${portalId}/requesttype?limit=$limit").cookie(acquireWebSudoCookies()).asObject(Map).body


        ArrayList<Map<String, Object>> requestTypes = rawResponse.getOrDefault("values", []) as ArrayList<Map<String, Object>>

        return requestTypes.collectEntries { [(it.get("name")): it.get("id") as Integer] }


    }


    /**
     * This will create a sample JSM project using the "IT Service Management" template
     * The project will contain issues
     * @param name Name of the new project
     * @param key Key of the new project
     * @return A ProjectBean
     */

    ProjectBean createJsmProjectWithSampleData(String name, String key) {

        return createDemoProject(name, key, "sd-demo-project-itil-v2")

    }


    /**
     * This will create a demo project with mock data using one of the project templates
     * The project will contain issues
     * Sets $adminUsername as project lead
     * @param name Name of the new project
     * @param projectKey Key of the new project
     * @param template One of the predefined templates:<br>
     *  IT Service management: sd-demo-project-itil-v2<br>
     *  Insight IT Service Management: rlabs-project-template-itsm-demodata<br>
     *  Project Management: core-demo-project<br>
     * @return A ProjectBean
     */
    ProjectBean createDemoProject(String name, String projectKey, String template) {


        log.info("Creating Project $name ($projectKey) with sample data using template $template")
        HttpResponse createProjectResponse
        try {
            createProjectResponse = rest.post("/rest/jira-importers-plugin/1.0/demo/create")
                    .cookie(getCookiesFromRedirect("/rest/project-templates/1.0/templates").cookies)
                    .cookie(acquireWebSudoCookies())
                    .connectTimeout(60000 * 8)
                    .header("X-Atlassian-Token", "no-check")
                    .field("name", name)
                    .field("key", projectKey.toUpperCase())
                    .field("lead", adminUsername)
                    .field("keyEdited", "false")
                    .field("projectTemplateWebItemKey", template)
                    .field("projectTemplateModuleKey", "undefined")
                    .asJson()
            assert createProjectResponse.status == 200, "Error creating project:" + createProjectResponse.body.toPrettyString()
        } catch (ex) {
            log.error("Error when creating Demo project:" + ex.message)
            throw ex
        }

        ProjectBean projectBean
        try {
            Map returnMap = createProjectResponse.body.getObject().toMap()
            projectBean = ProjectBean.fromMap(returnMap, this)

            log.info("\tCreated Project: ${projectBean.projectKey}")
            log.info("\t\tURL:" + (baseUrl + projectBean.returnUrl))
        } catch (ex) {
            log.error("Error when parsing data returned from API when creating Demo project:" + ex.message)
            throw ex
        }


        return projectBean

    }

    /**
     * Sets $adminUsername as project lead
     * @param name
     * @param key
     * @param template <br>
     *  JSM:<br>
     *  Basic JSM: com.atlassian.servicedesk:basic-service-desk-project<br>
     *  ITSM: com.atlassian.servicedesk:itil-v2-service-desk-project<br>
     *  Customer Service: com.atlassian.servicedesk:customer-service-desk-project<br>
     *  <br>
     *  Core:<br>
     *  PM: com.atlassian.jira-core-project-templates:jira-core-project-management<br>
     * @return
     */

    ProjectBean createNewProject(String name, String key, String template) {
        log.info("Creating Project $name ($key)")
        HttpResponse createProjectResponse = rest.post("/rest/project-templates/1.0/templates")
                .cookie(getCookiesFromRedirect("/rest/project-templates/1.0/templates").cookies)
                .cookie(acquireWebSudoCookies())
                .header("X-Atlassian-Token", "no-check")
                .field("name", name)
                .field("key", key)
                .field("lead", adminUsername)
                .field("keyEdited", "false")
                .field("projectTemplateWebItemKey", template)
                .field("projectTemplateModuleKey", template)
                .asJson()

        assert createProjectResponse.status == 200, "Error creating project:" + createProjectResponse.body.toPrettyString()

        Map returnMap = createProjectResponse.body.getObject().toMap()
        ProjectBean projectBean = ProjectBean.fromMap(returnMap, this)

        log.info("\tCreated Project:" + baseUrl + projectBean.returnUrl)

        return projectBean
    }

    /**
     * This will create a new JSM project with the "Basic" template
     * @param name Name of the new project
     * @param key Key of the new project
     */
    ProjectBean createJsmProject(String name, String key) {

        return createNewProject(name, key, "com.atlassian.servicedesk:basic-service-desk-project")


    }


    ArrayList<ProjectBean> getProjects(boolean useCache = true) {

        if (useCache && cached_Projects) {
            return cached_Projects
        }

        log.info("Retrieving projects from " + baseUrl)
        ArrayList<ProjectBean> projectBeans = []
        ArrayList<Map> rawList = rest.get("/rest/api/2/project").cookie(acquireWebSudoCookies()).asJson().body.getArray().toList()
        ArrayList<Map> massagedMap = rawList.collect { [returnUrl: "/projects/" + it.key, projectId: it.id as Integer, projectKey: it.key, projectName: it.name] }

        log.info("\tGot ${massagedMap.size()} projects")
        massagedMap.each {
            log.trace("\t\tTransforming raw project data for " + it.projectKey)
            projectBeans.add(ProjectBean.fromMap(it, this))
        }


        cached_Projects = projectBeans
        return projectBeans

    }

    boolean deleteProject(ProjectBean projectBean) {
        return deleteProject(projectBean.projectId)
    }

    boolean deleteProject(def idOrKey) {

        log.info("Deleting project:" + idOrKey.toString())
        Integer deleteStatus = rest.delete("/rest/api/2/project/" + idOrKey.toString()).cookie(acquireWebSudoCookies()).asEmpty().status
        return deleteStatus == 204


    }


    /** --- Issue CRUD --- **/

    ArrayList<IssueBean> jql(String jql) {

        ArrayList<Map> rawResponse = getJsonPages("/rest/api/2/search", [jql: jql], "issues")

        return IssueBean.fromArray(rawResponse)

    }

    IssueBean createIssue(String projectKey, String issueType, String summary, String description = "", String reporterName = "", String assigneeName = "", Map fieldValues = [:]) {

        fieldValues.each

        Map requestBody = [
                fields: [
                        project    : [
                                key: projectKey
                        ],
                        "assignee" : [name: assigneeName],
                        "reporter" : [name: reporterName],
                        summary    : summary,
                        description: description,
                        issuetype  : [
                                name: issueType
                        ]
                ] + fieldValues
        ]

        HttpResponse<Map> rawResponse = rest.post("/rest/api/2/issue").cookie(acquireWebSudoCookies()).contentType("application/json").body(requestBody).asObject(Map)

        return jql("key = \"${rawResponse.body.get("key")}\"").find { true }
    }


    /** --- Field CRUD --- **/


    /**
     * Create a new JIRA Customfield field
     * @param name Name of the new field
     * @param description (Optional) Description of the new field
     * @param searcherKey The key of the searcher to use, can be found using getFieldTypesInInstance()
     * @param typeKey The key of the type to use, can be found using getFieldTypesInInstance()
     * @param projectIds The projects to apply to, set to [] for all
     * @param issueTypeIds The IDs to apply to, set to [-1] for all
     * @return a new FieldBean
     */
    FieldBean createCustomfield(String name, String searcherKey, String typeKey, String description = "", ArrayList<String> projectIds = [], ArrayList<String> issueTypeIds = ["-1"]) {


        return FieldBean.createCustomfield(this, name, searcherKey, typeKey, description, projectIds, issueTypeIds)

    }


    /**
     * Get all custom fields
     * WIP: The underlying REST endpoint appears to have a bug with pagination.
     *  Dont trust this method 100% to return all fields
     * @param useCache returns cached fields if present and set to true
     * @return
     */
    ArrayList<CustomFieldBean> getCustomFields(boolean useCache = true) {

        if (useCache && cached_CustomFieldBeans) {
            return cached_CustomFieldBeans
        }

        cached_CustomFieldBeans = CustomFieldBean.getCustomFields(this)

        return cached_CustomFieldBeans
    }


    /**
     * Get all fields (System and Custom)
     * @param useCache If true, will return the same data as last time queried
     * @return
     */
    ArrayList<FieldBean> getFields(boolean useCache = true) {

        if (useCache && cached_FieldBeans) {
            return cached_FieldBeans
        }

        cached_FieldBeans = FieldBean.getFields(this)

        return cached_FieldBeans

    }

    /**
     * Delete a customFiled
     * @param fieldId ex: customfield_10000
     * @return true on success
     */
    boolean deleteCustomField(String fieldId) {
        return FieldBean.deleteCustomField(this, fieldId)
    }

    ArrayList<FieldBean.FieldType> getFieldTypes(boolean useCache = true) {

        if (useCache && cached_FieldTypes) {
            return cached_FieldTypes
        }

        cached_FieldTypes = FieldBean.FieldType.getFieldTypes(this)
        return cached_FieldTypes
    }

    @Deprecated
    ArrayList<String> getFieldIds(String fieldName) {

        ArrayList<Map<String, Object>> allFields = getFieldsRaw()

        ArrayList<Map<String, Object>> filteredFields = allFields.findAll { it.name == fieldName }

        ArrayList<String> filteredFieldIds = filteredFields?.id
        return filteredFieldIds

    }

    @Deprecated
    String getFieldId(String fieldName, String fieldType) {

        ArrayList<Map<String, Object>> allFields = getFieldsRaw()

        ArrayList<Map<String, Object>> filteredFields = allFields.findAll { it.name == fieldName && it?.schema?.type == fieldType }

        if (filteredFields.size() > 1) {
            throw new InputMismatchException("Found multiple fields wiht name \"$fieldName\" of type: \"$fieldType\":" + filteredFields.id.toString())
        } else if (filteredFields.size() == 1) {
            return filteredFields.first().id
        } else {
            return null
        }


    }

    @Deprecated
    ArrayList<Map<String, Object>> getFieldsRaw() {


        ArrayList<Map<String, Object>> rawResponse = rest.get("/rest/api/2/field").cookie(acquireWebSudoCookies()).asObject(new GenericType<ArrayList<Map<String, Object>>>() {
        }).body

        return rawResponse


    }

    /** --- Issue Type Actions --- **/


    ArrayList<IssueTypeBean> getIssueTypes(boolean useCache = true) {

        if (useCache && cached_IssueTypes) {
            return cached_IssueTypes
        }

        cached_IssueTypes = IssueTypeBean.getIssueTypes(this)

        return cached_IssueTypes

    }


    /** --- Scriptrunner Actions --- **/


    /**
     * Uses ScriptRunners (versions from V6.55.0) feature "Test Runner" to execute JUnit and SPOCK tests
     * This functionality in SR is HIGHLY unstable and can easily crash the entire JIRA instance
     *
     * This functionality is also quite picky with file names and locations
     *  <li>Files should be placed in $JIRAHOME/scripts/</li>
     *  <li>The filename must match class name </li>
     *  <li>The package must match the directory structure </li>
     *
     * @param packageToRun Corresponds to the "package" of the class to run
     * @param classToRun (Optional) Name of class in package to run
     * @param methodToRun (Optional) Name of method in class to run
     *
     * @return A SpockResult representing the test events
     */
    SpockResult runSpockTest(String packageToRun, String classToRun = "", String methodToRun = "") {

        if (methodToRun) {
            assert classToRun != "": "classToRun must be supplied when methodToRun is supplied"
        }

        String testToRun = packageToRun + (classToRun ? ".$classToRun" : "") + (methodToRun ? "#$methodToRun" : "")

        HttpResponse<Map> spockResponse = rest.post("/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.common.admin.RunUnitTests")
                .body(
                        [
                                "FIELD_TEST"         : [testToRun],
                                "FIELD_SCAN_PACKAGES": packageToRun,
                                "canned-script"      : "com.onresolve.scriptrunner.canned.common.admin.RunUnitTests"
                        ]
                )
                .contentType("application/json")
                .cookie(acquireWebSudoCookies())
                .connectTimeout(60000 * 8)
                .asObject(Map)

        assert spockResponse.status == 200: "Got unexpected HTTP Status when running Spock test"
        Map rawResponse = spockResponse.body
        SpockResult spockResult = SpockResult.fromString(rawResponse.json as String)

        return spockResult

    }

    /**
     * Uses ScriptRunners (versions prior to V6.55.0) feature "Test Runner" to execute JUnit and SPOCK tests
     * This functionality in SR is HIGHLY unstable and can easily crash the entire JIRA instance
     *
     * This functionality is also quite picky with file names and locations
     *  <li>Files should be placed in $JIRAHOME/scripts/</li>
     *  <li>The filename must match class name </li>
     *  <li>The package must match the directory structure </li>
     *
     * @param packageToRun Corresponds to the "package" of the class to run
     * @param classToRun (Optional) Name of class in package to run
     * @param methodToRun (Optional) Name of method in class to run
     *
     * @return A map containing the raw result from SR
     */
    @Deprecated
    LazyMap runSpockTestV6(String packageToRun, String classToRun = "", String methodToRun = "") {


        String testToRun = packageToRun

        if (classToRun) {
            testToRun += "." + classToRun
        }

        if (methodToRun) {
            testToRun += "#" + methodToRun
        }

        int fileNotFoundFails = 0

        HttpResponse spockResponse
        LazyMap spockOutput

        while (fileNotFoundFails <= 1) {

            spockResponse = rest.post("/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.common.admin.RunUnitTests")
                    .body(["FIELD_TEST": [testToRun], "FIELD_SCAN_PACKAGES": packageToRun])
                    .contentType("application/json")
                    .cookie(acquireWebSudoCookies())
                    .connectTimeout(60000 * 8)
                    .asJson()


            String spockOutputRaw = spockResponse.body.getObject().get("json")
            spockOutput = new JsonSlurper().parseText(spockOutputRaw) as LazyMap

            String initializationError = spockOutput.failedMethods?.initializationError ? spockOutput.failedMethods?.initializationError?.message : ""

            if (initializationError.startsWith("No tests found matching method name filter from")) {
                //If a spec file has just been uploaded, execution needs to be triggered twice, first to discover then to execute
                fileNotFoundFails += 1
            } else {
                break
            }
        }


        spockOutput.each { key, value ->


            if (value instanceof ArrayList && value.size() != 0) {

                if (!value.isEmpty()) {
                    log.info(key)
                    value.each { log.info("\t" + it) }
                }

            } else if (value instanceof LazyMap && value.size() != 0) {
                log.info(key)
                value.each { valueKey, valueValue ->
                    log.info("\t" + valueKey)


                    if (valueValue instanceof LazyMap) {
                        valueValue.each {
                            log.info("\t\t" + it.key)
                            it.value.toString().eachLine { log.info("\t\t\t" + it) }

                        }
                    } else {
                        log.info("\t\t" + valueValue)
                    }
                }
            }

        }

        return spockOutput

    }


    /**
     * Delete a scriptrunner file
     * @param filePath the path, relative to scriptrunner root
     * @return true on success
     */
    boolean deleteScriptrunnerFile(String filePath) {


        Cookies sudoCookies = acquireWebSudoCookies()

        HttpResponse<Empty> response = rest.delete("/rest/scriptrunner/latest/resource-directories/directory").cookie(sudoCookies).queryString("rootPath", srRoot).queryString("resourcePath", filePath).asEmpty()

        return response.status == 200

    }


    /**
     * Get the ScriptRunner script root
     * @return
     */
    String getSrRoot() {


        Cookies sudoCookies = acquireWebSudoCookies()

        HttpResponse scriptRootResponse = rest.get("/rest/scriptrunner/latest/idea/scriptroots").cookie(sudoCookies).asJson()


        ArrayList roots = new JsonSlurper().parseText(scriptRootResponse.body.toString()) as ArrayList
        assert roots && roots.size() == 1: "Could not determine script root, is scriptrunner installed?"
        LazyMap scriptRootRaw = roots[0] as LazyMap

        String scriptRoot = scriptRootRaw.get("info").get("rootPath")

        return scriptRoot

    }


    /**
     * Get the contents of a scriptrunner file
     * @param filePath Path, relative to scriptunner script root
     * @return
     */
    String getScriptrunnerFile(String filePath) {

        Cookies sudoCookies = acquireWebSudoCookies()

        HttpResponse<JsonNode> scriptRootResponse = rest.get("/rest/scriptrunner/latest/idea/file").queryString("filePath", filePath).queryString("rootPath", srRoot).cookie(sudoCookies).asJson()


        String rawScriptContent = scriptRootResponse.body.getObject().has("content") ? scriptRootResponse.body.getObject().get("content") : ""

        String scriptContent = new String(rawScriptContent.decodeBase64())

        return scriptContent


    }

    /**
     * Uploads a new, or updates an existing script file on the JIRA server
     * Files are normally placed in $JIRAHOME/scripts/...
     * @param scriptContent the text content of the script file
     * @param filePath The sub path (including file name) of the script root, where the file should be placed.  No leading "/"
     * @return true on success
     */

    boolean updateScriptrunnerFile(String scriptContent, String filePath) {


        Cookies sudoCookies = acquireWebSudoCookies()

        String scriptB64 = scriptContent.bytes.encodeBase64().toString()


        HttpResponse response = rest.put("/rest/scriptrunner/latest/idea/file").queryString("filePath", filePath.startsWith("/") ? filePath.substring(1) : filePath).queryString("rootPath", srRoot).contentType("application/octet-stream").cookie(sudoCookies).body(scriptB64).asEmpty()

        return response.status == 204


    }

    /**
     * Uploads a new, or updates an existing script file on the JIRA server
     * Files are normally placed in $JIRAHOME/scripts/...
     * @param scriptFile local file to upload
     * @param filePath The sub path (including file name) of the script root, where the file should be placed.  No leading "/"
     * @return true on success
     */
    boolean updateScriptrunnerFile(File scriptFile, String filePath) {

        return updateScriptrunnerFile(scriptFile.text, filePath)

    }

    /**
     * Uploads multiple new, or updates existing script files on the JIRA server
     * @param srcDest A map where the key is a source file or folder, and value is destination file or folder, ex:
     *     <p>"../src/someDir/someSubPath/"             :   "someDir/someSubPath/"
     *     <p>"../src/somOtherDir/SomeScript.groovy"    :   "somOtherDir/SomeScript.groovy"
     *
     * @return true on success
     */

    boolean updateScriptrunnerFiles(Map<String, String> srcDest) {

        log.info("Updating ${srcDest.size()} files/folders on remote jira ($baseUrl)")

        srcDest.each { srcFilePath, destFilePath ->

            File srcFile = new File(srcFilePath)


            if (srcFile.directory) {

                ArrayList<File> directoryFiles = []

                //Get direct files
                srcFile.eachFileMatch(~/.*.groovy/) { subFile ->
                    directoryFiles += subFile
                }

                //Get recursive files
                srcFile.eachDirRecurse { dir ->
                    dir.eachFileMatch(~/.*.groovy/) { file ->
                        directoryFiles += file
                    }
                }


                directoryFiles.each { subFile ->

                    String destinationPath = destFilePath + srcFile.relativePath(subFile)

                    destinationPath = destinationPath.startsWith("/") ? destinationPath.substring(1) : destinationPath

                    log.info("\tUpdating:" + subFile.name + ", Destination: " + destinationPath)
                    assert updateScriptrunnerFile(subFile.text, destinationPath), "Error updating " + subFile.name

                }


            } else {
                log.info("\tUpdating:" + srcFile.name)
                assert updateScriptrunnerFile(srcFile.text, destFilePath), "Error updating " + srcFile.name
            }

        }

        return true

    }

    /**
     * Executes a script using ScriptRunner
     * @param scriptContent Body of script
     * @return [log: logRows, errors: errorRows, success: boolean]
     */
    Map executeLocalScriptFile(String scriptContent) {


        HttpResponse scriptResponse = rest.post("/rest/scriptrunner/latest/user/exec/").connectTimeout(4 * 60000).cookie(acquireWebSudoCookies()).contentType("application/json").body(["script": scriptContent]).asJson()

        Map scriptResponseJson = scriptResponse.body?.getObject()?.toMap()

        if (!scriptResponseJson) {
            log.warn("Error getting response after executing ScriptRunner Script, got body:" + scriptResponse.body?.toPrettyString())
        }

        ArrayList<String> logRows = scriptResponseJson?.snapshot?.log?.split("\n") ?: []
        ArrayList<String> errorRows = scriptResponseJson?.errorMessages ?: [] as ArrayList<String>

        errorRows.each { log.info(it) }
        logRows.each { log.info(it) }


        Map returnMap = [log: logRows, errors: errorRows, success: (scriptResponse.status == 200)]
        return returnMap
    }

    /**
     * Executes a script using ScriptRunner
     * @param scriptFile scriptFile
     * @return [log: logRows, errors: errorRows, success: boolean]
     */
    Map executeLocalScriptFile(File scriptFile) {

        return executeLocalScriptFile(scriptFile.text)
    }

    /**
     * Cleares JIRA and Scriptrunner code caches, this usually breaks any script dependent on external libraries.
     * <p> Scriptrunner can be forced to rediscover the external libraries.
     * @param rediscoverApps (Optional), accepted apps (presuming they are installed) are:
     * <b>
     * <li>insight</li>
     * <li>tempo</li>
     * </b>
     *
     */
    void clearCodeCaches(ArrayList<String> rediscoverApps = []) {


        Cookies sudoCookies = acquireWebSudoCookies()


        //Handle newer Script-runner versions
        HttpResponse groovyCacheResponse = rest.post("/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.common.admin.ClearCache")
                .cookie(sudoCookies)
                .contentType("application/json")
                .asJson()
        assert groovyCacheResponse.body.getObject().toMap().output == "Groovy cache cleared."


        //Handle older Script-runner versions
        if (groovyCacheResponse.status >= 300) {
            groovyCacheResponse = rest.post("/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches")
                    .cookie(sudoCookies)
                    .body(["FIELD_WHICH_CACHE": "gcl"])
                    .contentType("application/json")
                    .asJson()

            assert groovyCacheResponse.body.getObject().toMap().output == "Groovy cache cleared."

            HttpResponse javaCacheResponse = rest.post("/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches")
                    .cookie(sudoCookies)
                    .body(["FIELD_WHICH_CACHE": "jira"])
                    .contentType("application/json")
                    .asJson()

            assert javaCacheResponse.body.getObject().toMap().output == "Jira cache cleared."

        }
        if (rediscoverApps) {

            String rediscoverPluginsScriptBody = "import com.onresolve.scriptrunner.runner.customisers.WithPlugin\n"
            rediscoverApps.each { app ->
                if (app == "insight") {
                    rediscoverPluginsScriptBody += "" +
                            "@WithPlugin(\"com.riadalabs.jira.plugins.insight\")\n" +
                            "import com.riadalabs.jira.plugins.insight.services.model.ObjectBean\n"
                } else if (app == "tempo") {
                    rediscoverPluginsScriptBody += "" +
                            "@WithPlugin ('is.origo.jira.tempo-plugin')\n" +
                            "import com.tempoplugin.worklog.v4.services.WorklogService\n"
                } else {
                    throw new InputMismatchException("Unknown app cant be rediscovered by SR:" + app)
                }
            }

            executeLocalScriptFile(rediscoverPluginsScriptBody)
        }


    }

    /**
     * Deletes a SR Scripted Rest Endpoint
     * @param endpointId
     * @return true on success
     */
    boolean deleteScriptedRestEndpointId(String endpointId) {

        Cookies cookies = acquireWebSudoCookies()

        HttpResponse response = rest.delete("/rest/scriptrunner/latest/custom/customadmin/$endpointId").cookie(cookies).asEmpty()

        return response.status == 204

    }

    /**
     * Gets ID of a SR Scripted Rest Endpoint
     * @param endpointName Display name of endpoint
     * @return ID
     */
    String getScriptedRestEndpointId(String endpointName) {

        log.info("Getting ID for REST Endpoint:" + endpointName)
        Cookies cookies = acquireWebSudoCookies()

        HttpResponse<ArrayList<Map>> response = rest.get("/rest/scriptrunner/latest/custom/customadmin?").cookie(cookies).asObject(new GenericType<ArrayList<Map>>() {
        })


        Map correctEndpoint = response.body.find { (it.get("endpoints") as ArrayList<Map>).any { it.name == endpointName } }

        return correctEndpoint?.id


    }


    /**
     * Create a new SR Scripted Rest endpoint
     * Nothing will stop you from creating multiple endpoints with the same name, but the result will be unpredictable.
     * This method is very picky, if Scriptrunner doesn't like your scripts syntax, it will fail
     *
     * Either scriptPath or scriptBody must be supplied
     *
     * @param scriptPath Path to existing script file. Files should be placed in $JIRAHOME/scripts/..
     * @param scriptBody Body of script if creating an inline script
     * @param description (Optional) Description of endpoint
     * @return true on suceess
     */
    boolean createScriptedRestEndpoint(String scriptPath = "", String scriptBody = "", String description = "") {

        //scriptPath: relative to the script folder

        assert !(scriptBody && scriptPath), "Both scriptPath and scriptBody cant be supplied at the same time"
        log.info("\tCreating RestEndpoint:")
        Cookies cookies = acquireWebSudoCookies()
        log.info("\t\t Acquired cookies:")

        HttpResponse response = rest.post("/rest/scriptrunner/latest/custom/customadmin/com.onresolve.scriptrunner.canned.common.rest.CustomRestEndpoint")
                .cookie(cookies)
                .body(["FIELD_NOTES": description, "FIELD_SCRIPT_FILE_OR_SCRIPT": ["scriptPath": (scriptPath != "" ? scriptPath : null), "script": (scriptBody != "" ? scriptBody : null)]])
                .contentType("application/json")
                .asEmpty()

        log.info("\t\tCreated RestEndpoint:")
        log.info("\t\t${response.status}")

        return response.status == 200
    }


    /**
     * Create a ScriptRunner job
     * @param jobNote Note of the job. Duplicates are allowed
     * @param userKey The JIRAUSER key of the user who should run the script
     * @param cron The cron schedule for the job
     * @param scriptPath The path to the script file to run, relative to the $JIRAHOME/script/ directory
     * @return a SrJob representing the job
     */
    SrJob createSrJob(String jobNote, String userKey, String cron, String scriptPath) {
        return SrJob.createJob(this, jobNote, userKey, cron, scriptPath)
    }


    /**
     * Get ScriptRunner jobs
     * @return
     */
    ArrayList<SrJob> getSrJobs() {

        return SrJob.getJobs(this)
    }


    /**
     * Delete a ScriptRunner job
     * @param jobId Id of the job to delete
     * @return true on success
     */
    boolean deleteSrJob(String jobId) {
        return SrJob.deleteJob(this, jobId)
    }


    /**
     * Deletes a Scriptrunner Database Resource
     * @param poolId id of the resource
     * @return true on success
     */
    boolean deleteLocalDbResourceId(String poolId) {

        Cookies cookies = acquireWebSudoCookies()

        HttpResponse response = rest.delete("/rest/scriptrunner/latest/resources/$poolId").cookie(cookies).asEmpty()

        return response.status == 204

    }

    /**
     * Get ID of a Scriptrunner Database Resource based on name
     * @param poolName Name of resource
     * @return ID
     */
    String getLocalDbResourceId(String poolName) {

        Cookies cookies = acquireWebSudoCookies()

        HttpResponse<ArrayList<Map>> response = rest.get("/rest/scriptrunner/latest/resources?").cookie(cookies).asObject(new GenericType<ArrayList<Map>>() {
        })


        Map correctEndpoint = response.body.find { it.get("canned-script") == "com.onresolve.scriptrunner.canned.db.LocalDatabaseConnection" && it.get("poolName") == poolName }

        return correctEndpoint?.id

    }

    /**
     * Create a Scriptrunner Database Resource of the "Local" type
     * @param poolName name of the new resource
     * @return true on success
     */
    boolean createLocalDbResource(String poolName) {

        Cookies cookies = acquireWebSudoCookies()

        HttpResponse response = rest.post("/rest/scriptrunner/latest/resources/com.onresolve.scriptrunner.canned.db.LocalDatabaseConnection")
                .cookie(cookies)
                .body(["poolName": poolName, "canned-script": "com.onresolve.scriptrunner.canned.db.LocalDatabaseConnection"])
                .contentType("application/json")
                .asEmpty()

        return response.status == 200
    }


    /** --- ScriptRunner - Script Fields --- **/


    /**
     * Get all ScriptRunner Script Fields, only tested for "Custom Script Field"
     * @return
     */
    ArrayList<ScriptFieldBean> getScriptFields() {

        return ScriptFieldBean.getScriptFields(this)


    }


    /**
     * A simplified method for creating a "Custom script field" with default settings
     * @param fieldName Name of the field
     * @param inlineBody The script body that will be used as inline script
     * @param template The template used by the field, default is textarea
     * @return the new ScriptFieldBean
     */
    ScriptFieldBean createCustomScriptField(String fieldName, String inlineBody, String template = "textarea") {
        ScriptFieldBean.createCustomScriptField(this, fieldName, inlineBody, template)
    }


    /**
     * WIP
     * @param group
     * @param module
     * @param version
     * @param repoUrl
     * @return
     */
    boolean installGroovyJarSources(String group, String module, String version, String repoUrl = "https://repo1.maven.org/maven2/") {

        log.info("Installing JAR source files")

        UnirestInstance unirestInstance = Unirest.spawnInstance()
        String jarName = "$module-$version-sources.jar"
        String jarPath = repoUrl + group.replaceAll(/\./, "/") + "/" + module + "/" + version + "/" + jarName

        log.info("\tFrom URL:" + jarPath)


        File tempDir = File.createTempDir()
        File extractDir = new File(tempDir.absolutePath + "/extract")
        extractDir.mkdirs()

        unirestInstance.get(jarPath).asFile(tempDir.absolutePath + "/" + jarName, StandardCopyOption.REPLACE_EXISTING).getBody()
        File jarFile = new File(tempDir.absolutePath + "/" + jarName)
        assert jarFile.canRead() && jarFile.isFile(): "Error downloading $jarPath"
        log.info("\tDownload appears successful, extracting jar")

        new ZipFile(jarFile.absolutePath).extractAll(extractDir.absolutePath)


        File sourceRoot = new File(extractDir.absolutePath + "/" + group.split(/\./).first())
        assert sourceRoot.exists() && sourceRoot.isDirectory()

        Map<String, String> filesToUpload = [:]
        sourceRoot.eachFileRecurse(FileType.FILES) { sourceFile ->


            String relativePath = Path.of(sourceRoot.parentFile.toURI()).relativize(Path.of(sourceFile.toURI()))

            filesToUpload.put(sourceFile.absolutePath, relativePath)
        }
        log.info("\tGot ${filesToUpload.size()} files from JAR archive, uploading them now")


        boolean uploadSuccess = updateScriptrunnerFiles(filesToUpload)

        uploadSuccess ? log.info("\tFiles where successfully uploaded") : log.warn("\tError uploading source files")
        tempDir.deleteDir()
        return uploadSuccess

    }

    /**
     * Installs a Grape dependency
     * Note clearCodeCaches() might needed to run after to apply changes
     * @param group
     * @param module
     * @param version
     * @param repoUrl
     * @param classifier
     * @return true on success
     */
    boolean installGrapeDependency(String group, String module, String version, String repoUrl = "", String classifier = "") {

        String installScript = ""

        if (repoUrl) {
            installScript += """
            @GrabResolver(root='$repoUrl', name='$module-repo')
            """
        }

        installScript += """
            @Grab(group='$group', module='$module', version='$version' ${classifier ? ", classifier = '$classifier'" : ""})
            
            import java.util.ArrayList //Something must be imported or script will fail
        """

        log.trace("Installing Grape dependencies with script:")
        installScript.eachLine { log.trace("\t" + it) }
        return !executeLocalScriptFile(installScript).errors


    }


    /**
     * Install InsightManager sources-files for use by ScriptRunner
     * @param branch (Optional, default is master)
     * @return true on success
     */
    boolean installInsightManagerSources(String branch = "master") {

        return installGroovySources("https://github.com/eficode/InsightManager", branch)

    }

    boolean installJiraInstanceMgrSources(String branch = "master") {

        return installGroovySources("https://github.com/eficode/JiraInstanceManagerRest", branch)

    }


    /**
     * Installs Groovy sources from a Github repo.
     * Requries that the sources be placed in $repoRoot/src/main/groovy/
     * @param githubRepoUrl ex: "https://github.com/eficode/InsightManager"
     * @param branch (Optional, default is master)
     * @return true on success
     */
    boolean installGroovySources(String githubRepoUrl, String branch = "master") {

        UnirestInstance githubRest = Unirest.spawnInstance()

        File tempDir = File.createTempDir()
        File unzipDir = new File(tempDir.canonicalPath, "unzip")
        assert unzipDir.mkdirs(): "Error creating temporary unzip dir:" + unzipDir.canonicalPath

        HttpResponse<File> downloadResponse = githubRest.get("$githubRepoUrl/archive/refs/heads/${branch}.zip").asFile((tempDir.canonicalPath.endsWith("/") ?: tempDir.canonicalPath + "/").toString() + "${branch}.zip")


        File zipFile = new File(tempDir.canonicalPath, branch + ".zip")
        assert zipFile.canRead(): "Error reading downloaded zip:" + zipFile.canonicalPath

        new ZipFile(zipFile.canonicalPath).extractAll(unzipDir.canonicalPath)

        File srcRoot
        unzipDir.eachDir {
            if (srcRoot == null && it.canonicalPath.endsWith(branch)) {
                srcRoot = new File(it.canonicalPath + "/src/main/groovy")
            }
        }

        Map<String, String> filesToUpload = [:]

        srcRoot.eachFileRecurse(FileType.FILES) {
            filesToUpload.put(it.canonicalPath, srcRoot.relativePath(it))
        }


        return updateScriptrunnerFiles(filesToUpload) && tempDir.deleteDir()


    }

    String getUserKey(String userName) {
        Cookies cookies = acquireWebSudoCookies()
        HttpResponse response = rest.get("/rest/api/2/user")
                .cookie(cookies)
                .header("Content-Type", "application/json")
                .queryString(["username": userName])
                .asJson()
        assert response.status == 200: "Error getting userKey"
        return response.body.getObject().toMap().key

    }

    UnirestInstance getUnirestInstance(boolean withBasicAuth = true) {
        UnirestInstance unirestInstance = Unirest.spawnInstance()
        unirestInstance.config().defaultBaseUrl(baseUrl).verifySsl(verifySsl)

        if (proxyPort && proxyhost) {
            unirestInstance.config().proxy(proxyhost, proxyPort)
        }


        if (withBasicAuth) {
            unirestInstance.config().setDefaultBasicAuth(adminUsername, adminPassword)
        }


        return unirestInstance
    }


}
