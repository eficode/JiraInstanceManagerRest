package com.eficode.atlassian.jiraInstanceManger

import groovy.json.JsonSlurper
import kong.unirest.*
import org.apache.groovy.json.internal.LazyMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import unirest.shaded.com.google.gson.JsonObject
import unirest.shaded.org.apache.http.NoHttpResponseException

final class JiraInstanceMangerRest {

    static Logger log = LoggerFactory.getLogger(JiraInstanceMangerRest.class)
    public static String baseUrl = "http://localhost:80"
    static Cookies cookies
    public static String adminUsername = "admin"
    public static String adminPassword = "admin"
    public static boolean useSamlNoSso = false //Not tested


    /**
     * Setup JiraInstanceMangerRest with admin/admin as credentials
     * @param BaseUrl Defaults to http://localhost:8080
     */
    JiraInstanceMangerRest(String BaseUrl = baseUrl) {
        baseUrl = BaseUrl
        Unirest.config().defaultBaseUrl(BaseUrl)

    }

    /**
     * Setup JiraInstanceMangerRest with custom credentials
     * @param BaseUrl Defaults to http://localhost:8080
     * @param username
     * @param password
     */
    JiraInstanceMangerRest(String username, String password, String BaseUrl = baseUrl) {
        baseUrl = BaseUrl
        Unirest.config().defaultBaseUrl(BaseUrl)
        adminUsername = username
        adminPassword = password

    }

    static Cookies extractCookiesFromResponse(HttpResponse response, Cookies existingCookies = null) {

        if (existingCookies == null) {
            existingCookies = new Cookies()
        }

        response.headers.all().findAll { it.name == "Set-Cookie" }.each {

            String name = it.value.split(";")[0].split("=")[0]
            String value = it.value.split(";")[0].split("=")[1]

            existingCookies.removeAll { it.name == name }
            existingCookies.add(new Cookie(name, value))

        }


        return existingCookies

    }

    /**
     * Generates cookies that are valid for a different user
     * Relies on scriptRunners Switch User script
     * @param userKey
     * @return a Cookies object for that user
     */
    static Cookies acquireUserCookies(String userKey) {

        log.info("Getting cookies for user:" + userKey)
        Cookies cookies = acquireWebSudoCookies()
        log.info("\tAquired admin cookies needed for user switch:" + cookies)
        log.info("\tTransforming admin cookies in to user cookies")

        UnirestInstance unirestInstance = Unirest.spawnInstance()
        unirestInstance.config().defaultBaseUrl(baseUrl)
        HttpResponse switchUserResponse = Unirest.post("/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.jira.admin.SwitchUser")
                .body(["FIELD_USER_ID": userKey, "canned-script": "com.onresolve.scriptrunner.canned.jira.admin.SwitchUser"])
                .contentType("application/json")
                .cookie(cookies)
                .asJson()


        unirestInstance.shutDown()
        assert switchUserResponse.status == 200, "Error getting cookies for user " + userKey

        UnirestInstance verifyInstance = Unirest.spawnInstance()
        verifyInstance.config().defaultBaseUrl(baseUrl)

        Map verifyMap = verifyInstance.get("/rest/api/2/myself").cookie(cookies).asJson().getBody().object.toMap()
        verifyInstance.shutDown()
        assert verifyMap.get("key") == userKey

        log.info("\tTransform of admin to user cookies appears successfull")
        log.info("\tReturning user cookies:" + cookies)

        return cookies
    }

    /**
     * Gets WebSudo cookies using the adminUsername and adminPassword credentials
     * @return
     */
    static Cookies acquireWebSudoCookies() {
        Map cookies = useSamlNoSso ? getCookiesFromRedirect("/secure/admin/WebSudoAuthenticate") : getCookiesFromRedirect("/login.jsp?nosso")

        UnirestInstance unirestInstance = Unirest.spawnInstance()
        unirestInstance.config().followRedirects(false).defaultBaseUrl(baseUrl)
        HttpResponse webSudoResponse = unirestInstance.post("/secure/admin/WebSudoAuthenticate.jspa")
                .cookie(cookies.cookies)
                .field("atl_token", cookies.cookies.find { it.name == "atlassian.xsrf.token" }.value)
                .field("webSudoPassword", adminPassword).asEmpty()


        unirestInstance.shutDown()
        assert webSudoResponse.status == 302, "Error acquiring Web Sudo"
        Cookies sudoCookies = webSudoResponse.cookies
        return sudoCookies
    }

    /**
     * Returns Array of insight schemas
     * @return
     */
    static ArrayList<Map> getInsightSchemas() {

        log.info("Getting Insight Schemas")

        Cookies cookies = acquireWebSudoCookies()
        ArrayList<Map> rawMap = Unirest.get("/rest/insight/1.0/objectschema/list").cookie(cookies).asJson().body.object.toMap().objectschemas as ArrayList<Map>
        log.trace("\tGot:" + rawMap.collect { [id: it?.id, key: it?.objectSchemaKey, name: it?.name, description: it?.description] })
        return rawMap

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
    static Map exportInsightSchema(String schemaName, String schemaId = null, String outputFileName, boolean includeObjects) {

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

        Map resultMap = Unirest.post("/rest/insight/1.0/objectschemaexport/export/server")
                .cookie(cookies)
                .contentType("application/json")
                .body(bodyMap)
                .asJson().body.object.toMap()

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
    static Map importInsightSchema(String fileName, String schemaName, String schemaKey, String schemaDescription = "", boolean includeObjects = false, boolean importAttachments = false, boolean importObjectAvatars = false) {

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

        HttpResponse validateResponse = Unirest.post("/rest/insight/1.0/objectschemaimport/import/server/nowarning").cookie(sudoCookies).body(bodyMap).header("Content-Type", "application/json").asJson()

        assert validateResponse.status == 200, "Error validating Import Schema parameters"

        HttpResponse importResponse = Unirest.post("/rest/insight/1.0/objectschemaimport/import/server").cookie(sudoCookies).body(bodyMap).header("Content-Type", "application/json").asJson()

        assert importResponse.status == 200, "Error starting import of Insight schema $fileName, :" + importResponse.body.toPrettyString()

        long schemaId = importResponse.body.object.get("resultData").objectSchemaId


        Map importProgress = importResponse.body.object.toMap()

        while (!importProgress.containsKey("progressInPercent") || importProgress.progressInPercent != 100) {

            HttpResponse progressResponse = Unirest.get("/rest/insight/1.0/progress/category/importobjectschema/" + schemaId).cookie(sudoCookies).asJson()
            importProgress = progressResponse.body.object.toMap()
            log.info("\tSchema import progress:" + importProgress.get("progressInPercent"))
            sleep(1000)

        }

        log.info("\tInsight schema import completed with status:" + importProgress.status)

        return importProgress
        //Map importResponseMap = importResponse.body.object.toMap()

    }

    /**
     * Install an App from Marketplace
     * @param appUrl Can be obtained by going to the marketplace listing of the app, checking its versions and getting the "Download" link URL
     *      Ex: https://marketplace.atlassian.com/download/apps/123/version/456
     * @param license (Optional) A license key for the app
     * @return a bool representing success
     */
    static boolean installApp(String appUrl, String license = null) {


        log.info("Installing App from URL:" + appUrl)
        Cookies sudoCookies = acquireWebSudoCookies()

        HttpResponse upmTokenResponse = Unirest.get("/rest/plugins/1.0/?").cookie(sudoCookies).asEmpty()
        String upmToken = upmTokenResponse.headers.getFirst("upm-token")

        HttpResponse installResponse = Unirest.post("/rest/plugins/1.0/?token=$upmToken").header("Content-Type", "application/vnd.atl.plugins.install.uri+json").body(["pluginUri": appUrl]).cookie(sudoCookies).asJson()

        Map installMap = installResponse.body.getObject().toMap()

        assert installMap.status.statusCode == 200, "Error Installing app: " + appUrl

        Map progress = [:]

        while (!progress.containsKey("done") || !progress.done) {

            HttpResponse taskProgress = Unirest.get(installMap.links.alternate).cookie(sudoCookies).asJson()

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

            HttpResponse currentLicenseResponse = Unirest.get(localAppUrl + "/license").cookie(sudoCookies).asJson()
            Map currentLicenseMap = currentLicenseResponse.body.getObject().toMap()
            String currentLicense = currentLicenseMap.rawLicense?.replaceAll("[\n\r]", "")

            if (currentLicense == newLicense) {
                log.info("\t\tThe license is already installed")
            } else {
                HttpResponse putLicenseResponse = Unirest.put(localAppUrl + "/license").contentType("application/vnd.atl.plugins+json").cookie(sudoCookies).body(["rawLicense": newLicense]).asJson()

                Map putLicenseResponseMap = putLicenseResponse.body.getObject().toMap()

                assert putLicenseResponse.status == 200 && putLicenseResponseMap.valid, "Error updating application license"

                log.info("\t\tThe license was successfully installed")

            }


        }


        return true

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
     * Unirest by default gets lost when several redirects return cookies, this method will retain them
     * @param path
     * @return
     */
    static Map getCookiesFromRedirect(String path, String username = adminUsername, String password = adminPassword, Map headers = [:]) {

        UnirestInstance unirestInstance = Unirest.spawnInstance()
        unirestInstance.config().followRedirects(false).defaultBaseUrl(baseUrl)

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


        unirestInstance.shutDown()
        return ["cookies": cookies, "lastResponse": getResponse]
    }


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
    static boolean setApplicationProperties(String jiraLicense, String appTitle = "Jira", String baseUrl = this.baseUrl) {

        log.info("Setting up JIRA Application Properties")
        Map redirectResponse = getCookiesFromRedirect("/")
        cookies = redirectResponse.cookies

        String setupAppPropertiesUrl = "/secure/SetupApplicationProperties.jspa"
        HttpResponse setAppProperties = Unirest.post(setupAppPropertiesUrl)
                .cookie(cookies)
                .field("atl_token", cookies.find { it.name == "atlassian.xsrf.token" }.value)
                .field("title", appTitle)
                .field("mode", "private")
                .field("baseURL", baseUrl)
                .field("nextStep", "true")
                .asString()

        assert setAppProperties.status == 302, "Error setting Application properties"
        log.info("\t\tSet title, mode and baseUrl successfully")

        log.info("Setting JIRA license, this will take a few minutes")


        String setLicenseUrl = "/secure/SetupLicense.jspa"


        HttpResponse setupLicenceResponse = Unirest.post(setLicenseUrl)
                .cookie(cookies)
                .field("setupLicenseKey", jiraLicense.replaceAll("[\n\r]", ""))
                .field("atl_token", cookies.find { it.name == "atlassian.xsrf.token" }.value)
                .socketTimeout(120000)
                .asJson()

        assert setupLicenceResponse.status == 302, "Error setting license"
        log.info("\t\tSet license successfully")

        log.info("\tSetting up admin account")
        String setupAdminUrl = "/secure/SetupAdminAccount.jspa"


        HttpResponse setupAdminResponse = Unirest.post(setupAdminUrl)
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


        HttpResponse setupEmailResponse = Unirest.post(setupEmailUrl)
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
    static boolean setupH2Database() {

        log.info("Setting up a blank H2 database for JIRA")
        long startTime = System.currentTimeMillis()
        Cookie xsrfCookie = null

        while (startTime + 60000 > System.currentTimeMillis()) {
            try {
                HttpResponse<String> response = Unirest.get("/").asString()

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

                assert ex.cause.class == NoHttpResponseException
                log.info("---- Jira not available yet ----")
                sleep(1000)
            }
        }

        if (System.currentTimeMillis() > startTime + 60000) {

            throw new NoHttpResponseException("Timeout waiting for JIRA Setup dialog")
        }

        log.info("Setting up local H2 database, this will take a few minutes.")
        HttpResponse setupDbResponse = Unirest.post("/secure/SetupDatabase.jspa")
                .field("databaseOption", "internal")
                .field("atl_token", xsrfCookie.value)
                .socketTimeout(240000)
                .asEmpty()

        assert setupDbResponse.status == 302
        assert setupDbResponse.headers.getFirst("Location").endsWith("SetupApplicationProperties!default.jspa")

        log.info("\tLocal database setup")
        return true


    }

    /**
     * This will create a sample JSM project using the "IT Service Management" template
     * @param name Name of the new project
     * @param key Key of the new project
     * @return A map containing the raw result from JIRAs api
     *  returnMap.returnUrl -> link to the project
     */

    static Map createJsmProjectWithSampleData(String name, String key) {

        log.info("Creating Project $name ($key) with sample data")
        HttpResponse createProjectResponse = Unirest.post("/rest/jira-importers-plugin/1.0/demo/create")
                .cookie(getCookiesFromRedirect("/rest/project-templates/1.0/templates").cookies)
                .cookie(acquireWebSudoCookies())
                .header("X-Atlassian-Token", "no-check")
                .field("name", name)
                .field("key", key.toUpperCase())
                .field("lead", adminUsername)
                .field("keyEdited", "false")
                .field("projectTemplateWebItemKey", "sd-demo-project-itil-v2")
                .field("projectTemplateModuleKey", "undefined")
                .asJson()

        assert createProjectResponse.status == 200, "Error creating project:" + createProjectResponse.body.toPrettyString()

        Map returnMap = createProjectResponse.body.object.toMap()

        log.info("\tCreated Project:" + baseUrl + returnMap.get("returnUrl"))
        return returnMap

    }


    /**
     * This will create a new JSM project with the "Basic" template
     * @param name Name of the new project
     * @param key Key of the new project
     * @return A map containing the raw result from JIRAs api
     *  returnMap.returnUrl -> link to the project
     */
    static Map createJsmProject(String name, String key) {

        log.info("Creating Project $name ($key)")
        HttpResponse createProjectResponse = Unirest.post("/rest/project-templates/1.0/templates")
                .cookie(getCookiesFromRedirect("/rest/project-templates/1.0/templates").cookies)
                .cookie(acquireWebSudoCookies())
                .header("X-Atlassian-Token", "no-check")
                .field("name", name)
                .field("key", key)
                .field("keyEdited", "false")
                .field("projectTemplateWebItemKey", "com.atlassian.servicedesk:basic-service-desk-project")
                .field("projectTemplateModuleKey", "com.atlassian.servicedesk:Abasic-service-desk-project")
                .asJson()

        assert createProjectResponse.status == 200, "Error creating project:" + createProjectResponse.body.toPrettyString()

        Map returnMap = createProjectResponse.body.object.toMap()

        log.info("\tCreated Project:" + baseUrl + returnMap.get("returnUrl"))

        return returnMap
    }

    /**
     * Uses ScriptRunners feature "Test Runner" to execute JUnit and SPOCK tests
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

    static LazyMap runSpockTest(String packageToRun, String classToRun = "", String methodToRun = "") {


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

            spockResponse = Unirest.post("/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.common.admin.RunUnitTests")
                    .body(["FIELD_TEST": [testToRun], "FIELD_SCAN_PACKAGES": packageToRun])
                    .contentType("application/json")
                    .cookie(acquireWebSudoCookies())
                    .socketTimeout(60000 * 8)
                    .asJson()


            String spockOutputRaw = spockResponse.body.object.get("json")
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
     * Uploads a new, or updates an existing script file on the JIRA server
     * Files are normally placed in $JIRAHOME/scripts/...
     * @param scriptContent the text content of the script file
     * @param filePath The sub path (including file name) of the script root, where the file should be placed.  No leading "/"
     * @return true on success
     */

    static boolean updateScriptrunnerFile(String scriptContent, String filePath) {

        String scriptB64 = scriptContent.bytes.encodeBase64().toString()

        Cookies sudoCookies = acquireWebSudoCookies()

        HttpResponse scriptRootResponse = Unirest.get("/rest/scriptrunner/latest/idea/scriptroots").cookie(sudoCookies).asJson()


        LazyMap scriptRootRaw = new JsonSlurper().parseText(scriptRootResponse.body.toString())[0] as LazyMap

        String scriptRoot = scriptRootRaw.get("info").get("rootPath")
        HttpResponse response = Unirest.put("/rest/scriptrunner/latest/idea/file?filePath=$filePath&rootPath=$scriptRoot").contentType("application/octet-stream").cookie(sudoCookies).body(scriptB64).asEmpty()

        return response.status == 204


    }

    /**
     * Uploads a new, or updates an existing script file on the JIRA server
     * Files are normally placed in $JIRAHOME/scripts/...
     * @param scriptFile local file to upload
     * @param filePath The sub path (including file name) of the script root, where the file should be placed.  No leading "/"
     * @return true on success
     */
    static boolean updateScriptrunnerFile(File scriptFile, String filePath) {

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

    static boolean updateScriptrunnerFiles(Map<String, String> srcDest) {

        log.info("Updating ${srcDest.size()} files/folders on remote jira ($baseUrl)")

        srcDest.each { srcFilePath, destFilePath ->

            File srcFile = new File(srcFilePath)

            if (srcFile.directory) {
                srcFile.eachFileMatch(~/.*.groovy/) { subFile ->
                    log.info("\tUpdating:" + subFile.name + ", Destination: " + (destFilePath + subFile.name))
                    assert updateScriptrunnerFile(subFile.text, (destFilePath + subFile.name)), "Error updating " + subFile.name
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
    static Map executeLocalScriptFile(String scriptContent) {


        HttpResponse scriptResponse = Unirest.post("/rest/scriptrunner/latest/user/exec/").socketTimeout(4 * 60000).cookie(acquireWebSudoCookies()).contentType("application/json").body(["script": scriptContent]).asJson()

        Map scriptResponseJson = scriptResponse.body.object.toMap()
        ArrayList<String> logRows = scriptResponseJson.snapshot?.log?.split("\n")
        ArrayList<String> errorRows = scriptResponseJson.errorMessages

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
    static Map executeLocalScriptFile(File scriptFile) {

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
    static void clearCodeCaches(ArrayList<String> rediscoverApps = []) {

        Cookies sudoCookies = acquireWebSudoCookies()

        HttpResponse groovyCacheResponse = Unirest.post("/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches")
                .cookie(sudoCookies)
                .body(["FIELD_WHICH_CACHE": "gcl"])
                .contentType("application/json")
                .asJson()

        assert groovyCacheResponse.body.object.toMap().output == "Groovy cache cleared."


        HttpResponse javaCacheResponse = Unirest.post("/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches")
                .cookie(sudoCookies)
                .body(["FIELD_WHICH_CACHE": "jira"])
                .contentType("application/json")
                .asJson()

        assert javaCacheResponse.body.object.toMap().output == "Jira cache cleared."

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
    static boolean deleteScriptedRestEndpointId(String endpointId) {

        Cookies cookies = acquireWebSudoCookies()

        HttpResponse response = Unirest.delete("/rest/scriptrunner/latest/custom/customadmin/$endpointId").cookie(cookies).asEmpty()

        return response.status == 204

    }

    /**
     * Gets ID of a SR Scripted Rest Endpoint
     * @param endpointName Display name of endpoint
     * @return ID
     */
    static String getScriptedRestEndpointId(String endpointName) {

        log.info("Getting ID for REST Endpoint:" + endpointName)
        Cookies cookies = acquireWebSudoCookies()

        HttpResponse response = Unirest.get("/rest/scriptrunner/latest/custom/customadmin?").cookie(cookies).asJson()
        List<JsonObject> endpointsRaw = response.body.array.toList()

        log.trace("\tRaw response:")
        endpointName.eachLine { log.trace("\t\t" + it) }


        Map correctEndpoint = endpointsRaw.find { it.get("endpoints").toList().any { it.name == endpointName } }?.toMap()

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
    static boolean createScriptedRestEndpoint(String scriptPath = "", String scriptBody = "", String description = "") {

        //scriptPath: relative to the script folder

        assert !(scriptBody && scriptPath), "Both scriptPath and scriptBody cant be supplied at the same time"
        log.info("\tCreating RestEndpoint:")
        Cookies cookies = acquireWebSudoCookies()
        log.info("\t\t Acquired cookies:")

        HttpResponse response = Unirest.post("/rest/scriptrunner/latest/custom/customadmin/com.onresolve.scriptrunner.canned.common.rest.CustomRestEndpoint")
                .cookie(cookies)
                .body(["FIELD_NOTES": description, "FIELD_SCRIPT_FILE_OR_SCRIPT": ["scriptPath": (scriptPath != "" ? scriptPath : null), "script": (scriptBody != "" ? scriptBody : null)]])
                .contentType("application/json")
                .asEmpty()

        log.info("\t\tCreated RestEndpoint:")
        log.info("\t\t${response.status}")

        return response.status == 200
    }


    /**
     * Deletes a Scriptrunner Database Resource
     * @param poolId id of the resource
     * @return true on success
     */
    static boolean deleteLocalDbResourceId(String poolId) {

        Cookies cookies = acquireWebSudoCookies()

        HttpResponse response = Unirest.delete("/rest/scriptrunner/latest/resources/$poolId").cookie(cookies).asEmpty()

        return response.status == 204

    }

    /**
     * Get ID of a Scriptrunner Database Resource based on name
     * @param poolName Name of resource
     * @return ID
     */
    static String getLocalDbResourceId(String poolName) {

        Cookies cookies = acquireWebSudoCookies()

        HttpResponse response = Unirest.get("/rest/scriptrunner/latest/resources?").cookie(cookies).asJson()
        List<JsonObject> resourcesRaw = response.body.array.toList()


        Map correctEndpoint = resourcesRaw.find { it.get("canned-script") == "com.onresolve.scriptrunner.canned.db.LocalDatabaseConnection" && it.get("poolName") == poolName }?.toMap()

        return correctEndpoint?.id

    }

    /**
     * Create a Scriptrunner Database Resource of the "Local" type
     * @param poolName name of the new resource
     * @return true on success
     */
    static boolean createLocalDbResource(String poolName) {

        Cookies cookies = acquireWebSudoCookies()

        HttpResponse response = Unirest.post("/rest/scriptrunner/latest/resources/com.onresolve.scriptrunner.canned.db.LocalDatabaseConnection")
                .cookie(cookies)
                .body(["poolName": poolName, "canned-script": "com.onresolve.scriptrunner.canned.db.LocalDatabaseConnection"])
                .contentType("application/json")
                .asEmpty()

        return response.status == 200
    }


}