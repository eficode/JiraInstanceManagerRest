package com.eficode.atlassian.jiraInstanceManger

import com.eficode.atlassian.jiraInstanceManger.beans.ObjectSchemaBean
import com.eficode.atlassian.jiraInstanceManger.beans.ProjectBean
import kong.unirest.Cookies
import kong.unirest.HttpResponse
import kong.unirest.Unirest
import kong.unirest.UnirestInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

class JiraInstanceManagerRestSpec extends Specification {

    @Shared
    static Logger log = LoggerFactory.getLogger(JiraInstanceMangerRest.class)

    @Shared
    static String baseUrl = "http://jira.test.com:8080"

    @Shared
    static String restAdmin = "admin"

    @Shared
    static String restPw = "admin"

    @Shared
    static Cookies sudoCookies

    def setupSpec() {

        JiraInstanceMangerRest.baseUrl = baseUrl

        Unirest.config().defaultBaseUrl(JiraInstanceMangerRest.baseUrl).setDefaultBasicAuth(restAdmin, restPw)
        sudoCookies = JiraInstanceMangerRest.acquireWebSudoCookies()
    }


    def "Test Installation of Grapes"() {

        setup:
        JiraInstanceMangerRest jira = new JiraInstanceMangerRest()

        String grapeGroup = "org.apache.httpcomponents"
        String grapeModule = "httpclient"
        String grapeVersion = "4.5.13"
        String importTest = "import org.apache.http.client.methods.HttpGet"

        String importTestScript = """
        @Grab(group="$grapeGroup", module ="$grapeModule", version = "$grapeVersion")
        $importTest

        """


        expect:
        jira.installGrapeDependency(grapeGroup, grapeModule, grapeVersion)
        jira.clearCodeCaches()
        !jira.executeLocalScriptFile(importTestScript).errors


    }

    def "Simple getProjectsTest"() {

        setup:
        JiraInstanceMangerRest jiraR = new JiraInstanceMangerRest()

        expect:
        !jiraR.getProjects().empty
        jiraR.getProjects().every {it instanceof ProjectBean}

    }

    def "Test getting arbitrary user cookies"() {


        setup: "Create sample data"

        String spocUsername = "spoc_" + System.currentTimeSeconds()
        String spocUserKey = '""'


        String userCrudScript = """
            import com.atlassian.jira.component.ComponentAccessor
            import com.atlassian.jira.security.JiraAuthenticationContext
            import com.atlassian.jira.user.ApplicationUser
            import com.atlassian.jira.user.UserDetails
            import com.atlassian.jira.user.util.UserManager
            import com.atlassian.jira.user.util.UserUtil
            import org.apache.log4j.Level
            
            UserManager userManager = ComponentAccessor.getUserManager()
            UserUtil userUtil = ComponentAccessor.getUserUtil()
            JiraAuthenticationContext authenticationContext = ComponentAccessor.getJiraAuthenticationContext()
            
            
            String username = "$spocUsername"
            
            log.setLevel(Level.ALL)
            
            
            if (CREATE_USER) {
                log.info("Creating user $spocUsername")
            
                UserDetails userDetails = new UserDetails(username, username)
            
                ApplicationUser newUser = userManager.createUser(userDetails)
            
                log.info("Created user with key:" + newUser.key)
            }
            
            if (DELETE_USER) {
                log.info("Deleting user $spocUsername (SPOC_USER_KEY)")
                ApplicationUser userToDelete = userManager.getUserByKey("SPOC_USER_KEY")
                userUtil.removeUser(authenticationContext.loggedInUser, userToDelete)
            
            }

        """

        log.info("Creating sample data for testing acquireUserCookies()")
        JiraInstanceMangerRest jira = new JiraInstanceMangerRest()
        UnirestInstance spocInstance = Unirest.spawnInstance()
        spocInstance.config().defaultBaseUrl(JiraInstanceMangerRest.baseUrl)


        log.info("\tCreating spoc user:" + spocUsername)
        Map createUserResult = JiraInstanceMangerRest.executeLocalScriptFile(userCrudScript.replace(["CREATE_USER": "true", "DELETE_USER": "false", "USERNAME_INPUT": spocUsername]))
        assert createUserResult.success
        spocUserKey = createUserResult.log.last().replaceFirst(".*Created user with key:", "")
        assert spocUserKey: "Error getting user key for spoc user with name $spocUsername, should be in log line:" + createUserResult.log.last()

        when: "Getting user cookies"
        log.info("\tGetting cookies for user:" + spocUserKey)
        Cookies userCookies = jira.acquireUserCookies(spocUserKey)
        log.info("\t\tGot:" + userCookies)

        then:
        assert !userCookies.empty: "No user cookies where returned"

        when:
        log.info("\tQuerying REST for self")
        Map selfMap = spocInstance.get("/rest/api/2/myself").cookie(userCookies).asJson().getBody().object.toMap()
        log.info("\t\tGot:" + selfMap.findAll { ["key", "name"].contains(it.key) })

        then:
        selfMap.key == spocUserKey
        selfMap.name == spocUsername
        log.info("\tRest API indicates the cookie is functional")

        cleanup:
        spocInstance.shutDown()
        Map deleteUserResult = JiraInstanceMangerRest.executeLocalScriptFile(userCrudScript.replace(["CREATE_USER": "false", "DELETE_USER": "true", "SPOC_USER_KEY": spocUserKey]))
        assert deleteUserResult.success
        log.info("\tSpoc user was deleted")


    }


    def "Test CRUD of SR Local DB Resource"() {


        when: "Instantiate JiraInstanceManager"

        JiraInstanceMangerRest jira = new JiraInstanceMangerRest()

        then:
        assert JiraInstanceMangerRest.createLocalDbResource("spoc-pool"): "Error creating Local DB Resource"
        log.info("DB Resource created")

        when: "Querying for the ID of the pool"
        String poolId = JiraInstanceMangerRest.getLocalDbResourceId("spoc-pool")
        log.info("getLocalDbResourceId returned:" + poolId)

        then:
        poolId
        poolId.size() > 10
        log.info("getLocalDbResourceId: appears to function")

        then:
        assert JiraInstanceMangerRest.deleteLocalDbResourceId(poolId)
        assert JiraInstanceMangerRest.getLocalDbResourceId("spoc-pool") == null
        log.info("deleteLocalDbResourceId: appears to function")

    }


    def "Test creation of SR Rest endpoint"() {
        setup: "Instantiate JiraInstanceManager"

        JiraInstanceMangerRest jira = new JiraInstanceMangerRest()


        String endpointScriptBody = """
            import com.onresolve.scriptrunner.runner.rest.common.CustomEndpointDelegate
            import groovy.json.JsonBuilder
            import groovy.transform.BaseScript
            
            import javax.ws.rs.core.MediaType
            import javax.ws.rs.core.MultivaluedMap
            import javax.ws.rs.core.Response
            
            @BaseScript CustomEndpointDelegate delegate
            
            spocTest(httpMethod: "GET", groups: ["jira-administrators"]) { MultivaluedMap queryParams, String body ->
            
            
            
                return Response.ok(new JsonBuilder([status: "working" ]).toString(), MediaType.APPLICATION_JSON).build();
            }
           
            """
        endpointScriptBody = endpointScriptBody.replace("\t", "")

        when: "Creating a rest endpoint with script text"
        log.info("Creating scripted rest endpoint with Script body")
        boolean createResult = JiraInstanceMangerRest.createScriptedRestEndpoint("", endpointScriptBody, "A description")
        log.info("\tGetting response from the new endpoint")
        HttpResponse queryNewEndpointResponse = Unirest.get("/rest/scriptrunner/latest/custom/spocTest").asJson()
        log.info("\t\tGot:" + queryNewEndpointResponse.body.toString())

        then: "The createResult should be true, and the endpoint should return the expected status and data"
        createResult
        queryNewEndpointResponse.status == 200
        queryNewEndpointResponse.body.object.get("status") == "working"

        then: "Deleting the new endpoint should succeed"
        JiraInstanceMangerRest.deleteScriptedRestEndpointId(JiraInstanceMangerRest.getScriptedRestEndpointId("spocTest"))


        when: "Creating a rest endpoint with script file"
        log.info("Creating scripted rest endpoint with Script file")
        assert JiraInstanceMangerRest.updateScriptrunnerFile(endpointScriptBody, "spocTestEndpoint.groovy"): "Error creating script file"
        log.info("\tCreated script file")

        createResult = JiraInstanceMangerRest.createScriptedRestEndpoint("spocTestEndpoint.groovy", "", "A description")
        log.info("\tGetting response from the new endpoint")
        queryNewEndpointResponse = Unirest.get("/rest/scriptrunner/latest/custom/spocTest").asJson()
        log.info("\t\tGot:" + queryNewEndpointResponse.body.toString())

        then: "The createResult should be true, and the endpoint should return the expected status and data"
        createResult
        queryNewEndpointResponse.status == 200
        queryNewEndpointResponse.body.object.get("status") == "working"

        then: "Deleting the new endpoint should succeed"
        JiraInstanceMangerRest.deleteScriptedRestEndpointId(JiraInstanceMangerRest.getScriptedRestEndpointId("spocTest"))


    }

    def "Test creation of Insight sample project"() {

        setup: "Instantiate JiraInstanceManager"

        log.info("Will test creation of Insight sample project")




        JiraInstanceMangerRest jira = new JiraInstanceMangerRest()
        jira.acquireWebSudoCookies()

        String projectName = "Spoc Src Schema"
        String projectKey = jira.getAvailableProjectKey("SSS")

        ArrayList<Integer> preExistingSchemaIds = jira.getInsightSchemas().id

        when:"When creating the project"
        Map resultMap = jira.createInsightProjectWithSampleData(projectName, projectKey)

        then: "Project and schema should be returned"
        assert resultMap.project
        assert resultMap.project.projectKey == projectKey
        assert resultMap.project.projectName == projectName
        assert resultMap.schema
        assert ! preExistingSchemaIds.contains(resultMap.schema.id as int)
        assert jira.projects.find {it.projectId == resultMap.project.projectId} : "getProjects() could not find project"
        log.info("\tSchema and project successfully created")



        cleanup:
        jira.deleteInsightSchema(resultMap.schema.id as int)
        jira.deleteProject(resultMap.project)





    }

    def "Test Import and Export of Insight Object Schemas"() {
        setup: "Instantiate JiraInstanceManager"

        log.info("Will test export and import of insight object schemas")

        JiraInstanceMangerRest jira = new JiraInstanceMangerRest()
        jira.acquireWebSudoCookies()

        String srcSchemaName = "Spoc Src Schema"
        String srcSchemaKey = "SSS"
        String srcSchemaTemplate = "hr"
        log.info("\tWill first create a new schema")
        log.debug("\t\tSource schema name:" + srcSchemaName)
        log.debug("\t\tSource schema key:" + srcSchemaKey)
        log.debug("\t\tSource schema template:" + srcSchemaTemplate)

        Map sampleSchemaMap = Unirest.post("/rest/insight/1.0/objectschemaimport/template")
                .cookie(sudoCookies)
                .contentType("application/json")
                .body([
                        "status"         : "ok",
                        "name"           : srcSchemaName,
                        "objectSchemaKey": srcSchemaKey,
                        "type"           : srcSchemaTemplate

                ]).asJson().body.object.toMap()

        log.info("\tSchema created with status ${sampleSchemaMap.status} and Id: " + sampleSchemaMap.id)
        assert sampleSchemaMap.status == "Ok", "Error creating sample schema"

        when: "Exporting the new sample schema"
        log.info("Exporting the new sample schema")
        Map exportResult = JiraInstanceMangerRest.exportInsightSchema(sampleSchemaMap.name as String, sampleSchemaMap.id as String, "anOutput", true)
        String exportFile = exportResult.resultData.exportFileName as String
        log.info("\tExport result:" + exportResult?.result)

        then:
        exportResult.status == "FINISHED"
        exportResult.result == "OK"
        exportFile.contains("anOutput")
        exportFile.endsWith(".zip")


        when: "Importing the newly created export"

        String moveFileToImportScriptBody = """
        import java.nio.file.Files
        import java.nio.file.StandardCopyOption
        File srcFile = new File("/var/atlassian/application-data/jira/export/insight/${exportFile}")
        File destFile = new File("/var/atlassian/application-data/jira/import/insight/${exportFile}")
        Files.move(srcFile.toPath(),destFile.toPath(), StandardCopyOption.REPLACE_EXISTING )
        """

        log.info("Moving the newly created export file to the import directory")
        log.debug("\tMoving to: /var/atlassian/application-data/jira/import/insight/$exportFile")
        JiraInstanceMangerRest.executeLocalScriptFile(moveFileToImportScriptBody)


        log.info("Importing the newly created export")
        Map importResult = JiraInstanceMangerRest.importInsightSchema(exportFile, srcSchemaName + " reImported", srcSchemaKey + "imp", srcSchemaName, true)

        log.info("\tImport result:" + importResult?.result)
        String importSchemaId = importResult.resourceId

        String importFile = importResult.resultData.fileName as String


        then:
        importResult.status == "FINISHED"
        importResult.result == "OK"
        importFile?.contains("anOutput")
        importFile?.endsWith(".zip")


        when: "Making sure getInsightSchemas() finds the schemas"
        ArrayList<ObjectSchemaBean> schemas = JiraInstanceMangerRest.getInsightSchemas()

        log.trace("getInsightSchemas() returned schemas:" + schemas.name)

        then: "Should contain both schemas"
        assert schemas.find { it.id == sampleSchemaMap.id && it.objectSchemaKey == sampleSchemaMap.objectSchemaKey } : "Expected API to find source schema (Key: ${sampleSchemaMap.objectSchemaKey}, Id: ${sampleSchemaMap.id} )"
        assert schemas.find {it.objectSchemaKey == srcSchemaKey + "IMP" }

        expect: "Deleting the schema"
        assert JiraInstanceMangerRest.deleteInsightSchema(sampleSchemaMap.id as int) : "Error deleting schema"
        assert ! JiraInstanceMangerRest.getInsightSchemas().find { it.id == sampleSchemaMap.id && it.key == sampleSchemaMap.key } : "After schema deletion, API still says the schema exists"


        cleanup:
        log.info("\tFinished testing import and export of object schemas")
        Unirest.delete("/rest/insight/1.0/objectschema/" + sampleSchemaMap.id).cookie(sudoCookies).asEmpty()
        Unirest.delete("/rest/insight/1.0/objectschema/" + importSchemaId).cookie(sudoCookies).asEmpty()

    }

}