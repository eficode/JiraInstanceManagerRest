package com.eficode.atlassian.jiraInstanceManager

import com.eficode.atlassian.jiraInstanceManager.beans.AssetFieldBean
import com.eficode.atlassian.jiraInstanceManager.beans.IssueBean
import com.eficode.atlassian.jiraInstanceManager.beans.JiraApp
import com.eficode.atlassian.jiraInstanceManager.beans.FieldBean
import com.eficode.atlassian.jiraInstanceManager.beans.MarketplaceApp
import com.eficode.atlassian.jiraInstanceManager.beans.ObjectSchemaBean
import com.eficode.atlassian.jiraInstanceManager.beans.ProjectBean
import com.eficode.atlassian.jiraInstanceManager.beans.SpockResult
import com.eficode.atlassian.jiraInstanceManager.beans.SrJob
import com.eficode.devstack.deployment.impl.JsmH2Deployment
import de.gesellix.docker.remote.api.ContainerState
import groovy.io.FileType
import kong.unirest.core.Cookies
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import kong.unirest.core.UnirestInstance
import org.apache.groovy.json.internal.LazyMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification


class JiraInstanceManagerRestSpec extends Specification {

    @Shared
    static Logger log = LoggerFactory.getLogger(JiraInstanceManagerRest.class)

    @Shared
    static String baseUrl = "http://jira.localhost:8080"

    @Shared
    static String baseSrVersion = "latest"

    @Shared
    static JsmH2Deployment jsmDep = new JsmH2Deployment(baseUrl)

    @Shared
    static boolean reuseContainer = true //If true and container is already setup, it will be re-used.

    @Shared
    static String jsmLicense = new File(System.getProperty("user.home") + "/.licenses/jira/jsm.license").text
    @Shared
    static String srLicense = new File(System.getProperty("user.home") + "/.licenses/jira/sr.license").text


    @Shared
    static String restAdmin = "admin"

    @Shared
    static String restPw = "admin"

    @Shared
    static Cookies sudoCookies

    def setupSpec() {

        Unirest.config().defaultBaseUrl(baseUrl).setDefaultBasicAuth(restAdmin, restPw)


        jsmDep.setJiraLicense(jsmLicense)
        jsmDep.appsToInstall.put(MarketplaceApp.getScriptRunnerVersion(baseSrVersion).getDownloadUrl(), srLicense)

        if (!(reuseContainer && jsmDep?.jsmContainer?.status() == ContainerState.Status.Running)) {

            //Stop and remove if already existing
            jsmDep.stopAndRemoveDeployment()

            //Start and wait for the deployment
            jsmDep.setupDeployment()
            jsmDep.jiraRest.waitForJiraToBeResponsive()

            assert jiraInstanceManagerRest.installScriptRunner(srLicense, baseSrVersion): "Error installing SR version:" + baseSrVersion

        }

        sudoCookies = new JiraInstanceManagerRest(restAdmin, restPw, baseUrl).acquireWebSudoCookies()
        assert sudoCookies && jsmDep.jsmContainer.status() == ContainerState.Status.Running
    }

    JiraInstanceManagerRest getJiraInstanceManagerRest() {
        JiraInstanceManagerRest jim = new JiraInstanceManagerRest(restAdmin, restPw, baseUrl)
        //jim.setProxy("localhost", 8090)
        return jim
    }


    def "Test scriptRunnerIsInstalled"() {

        setup:
        JiraInstanceManagerRest jira = getJiraInstanceManagerRest()

        expect:
        jira.scriptRunnerIsInstalled()

    }

    def "Make sure multiple instances of the class stay independent"() {

        setup:
        JiraInstanceManagerRest jira1 = new JiraInstanceManagerRest(baseUrl, restAdmin, restPw)
        JiraInstanceManagerRest jira2 = new JiraInstanceManagerRest(baseUrl + 2, restAdmin + 2, restPw + 2)


        expect:
        jira1.rest.config().getDefaultBaseUrl() != jira2.rest.config().getDefaultBaseUrl()
        jira1.baseUrl != jira2.baseUrl


    }

    def "Test App Crud"() {

        setup:
        JiraInstanceManagerRest jira = getJiraInstanceManagerRest()

        String testAppKey = "com.atlassian.labs.rest-api-browser"
        String testAppName = "Atlassian REST API Browser"
        MarketplaceApp.Hosting testAppHostingType = MarketplaceApp.Hosting.Server

        jira.installedApps.findAll { it.key == testAppKey }.each {
            assert jira.uninstallApp(it): "Error uninstalling app in preparation for test:" + testAppKey
        }

        when: "Searching for the app"
        log.info("Searching marketplace for app with name $testAppName and matching key:" + testAppKey)
        ArrayList<MarketplaceApp> matchingApps = jira.searchMarketplace(testAppName, testAppHostingType)
        MarketplaceApp marketplaceApp = matchingApps.find { it.key == testAppKey }

        then: "The app should be found"
        assert marketplaceApp && marketplaceApp.key == testAppKey: "Error finding test App in marketplace"
        log.info("\tFound matching app in marketplace")

        when: "Installing the app with default parameters"
        log.info("Installing app using returned marketplace metadata, with default values")
        assert jira.installApp(marketplaceApp, testAppHostingType): "Error installing marketplace app"
        JiraApp jiraApp = jira.installedApps.find { it.key == testAppKey }
        log.info("\t" + jiraApp?.toString() + " was installed")

        then: "The installation should be successful and the app should now be listed as installed"
        jiraApp.version == marketplaceApp.getLatestVersion().name
        assert jira.installedApps.find { it.key == testAppKey && it.version == marketplaceApp.embedded.version.name } != null: "App was not found in list of installed apps"
        log.info("\tInstallation was successful")

        when: "Uninstalling the app"
        log.info("Uninstalling the app again")
        assert jira.uninstallApp(jiraApp): "Error uninstalling app"

        then: "It should no longer be listed as installed"
        assert jira.installedApps.find { it.key == testAppKey } == null: "App was still found in list of installed apps"
        log.info("\tSuccessfully uninstalled app")

        when: "Installing an older version of the app"
        MarketplaceApp.Version oldVersion = marketplaceApp.getVersions(10, testAppHostingType)[1]
        log.info("Installing old version of App:" + oldVersion.toString())
        assert jira.installApp(marketplaceApp, testAppHostingType, oldVersion.name): "Error installing marketplace app with old version:" + oldVersion.name

        then:
        assert jira.installedApps.find { it.key == testAppKey && it.version == oldVersion.name } != null: "App was not found in list of installed apps, after installing old version"
        log.info("\tSuccessfully installed old version of app")

        expect: "Check minor features"
        assert marketplaceApp.getVersion(oldVersion.name, testAppHostingType)?.name == oldVersion.name: "Error retrieving specific version of marketplace app"

    }

    def "Test installation of Jar sources"() {

        setup:
        JiraInstanceManagerRest jira = new JiraInstanceManagerRest(baseUrl)


        String group = "com.eficode.atlassian"
        String module = "jirainstancemanager"
        String version = "1.1.0-SNAPSHOT"
        String repoUrl = "https://github.com/eficode/JiraInstanceManagerRest/raw/packages/repository/"

        expect:
        jira.installGroovyJarSources(group, module, version, repoUrl)

    }


    Map<File, ArrayList<File>> createSampleGroovyFiles() {

        File rootDir = File.createTempDir()
        ArrayList<File> files = []


        [
                "com/spoc/subSubFile.groovy": "log.info(\"com/spoc/subSubFile.groovy\")",
                "rootFile.groovy"           : "log.info(\"rootFile.groovy\")",
                "com/subFile.groovy"        : "log.info(\"com/subFile.groovy\")",
        ].each { path, content ->

            String fileRelPath = path.contains("/") ? path.substring(0, path.lastIndexOf("/")) : ""

            if (fileRelPath) {
                new File(rootDir, fileRelPath).mkdirs()
            }

            File newFile = new File(rootDir, path)
            assert newFile.createNewFile(): "Error creating file:" + path
            newFile.text = content
            files += newFile
        }


        return [(rootDir): files]
    }

    def "Test updateScriptrunnerFiles with recursive files"() {

        setup:
        JiraInstanceManagerRest jim = jiraInstanceManagerRest


        Map<File, ArrayList<File>> sampleFilesMap = createSampleGroovyFiles()
        File localSampleRoot = sampleFilesMap.keySet().first()
        ArrayList<File> localSampleFiles = sampleFilesMap.values().first()
        Map<String, String> uploadParameters = [:]

        localSampleFiles.each {
            jim.deleteScriptrunnerFile(localSampleRoot.relativePath(it))

        }

        when: "Using a map with recursive files"

        assert jim.updateScriptrunnerFiles([(localSampleRoot.canonicalPath): "/"]): "Error updating ScriptRunner files"

        then: "SR should have the same directory structure"
        localSampleFiles.each {
            assert jim.getScriptrunnerFile(localSampleRoot.relativePath(it)): "Failed to receive uploaded file:" + localSampleRoot.relativePath(it)

        }

        when: "Deleting the files"
        localSampleFiles.each {
            assert jim.deleteScriptrunnerFile(localSampleRoot.relativePath(it)): "Failed to receive uploaded file:" + localSampleRoot.relativePath(it)

        }
        then: "SR should no longer return them"
        localSampleFiles.each {
            assert !jim.getScriptrunnerFile(localSampleRoot.relativePath(it)): "Failed to receive uploaded file:" + localSampleRoot.relativePath(it)

        }


        cleanup:

        assert localSampleRoot.deleteDir(): "Error cleaning up sample groovy files:" + localSampleRoot.canonicalPath


    }

    def "Test Installation of Grapes"() {

        setup:
        JiraInstanceManagerRest jira = new JiraInstanceManagerRest(baseUrl)

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

    def "Test SR Job Crud"(String srVersionNumber, boolean last) {

        setup:
        log.info("Testing CRUD of SR jobs")
        JiraInstanceManagerRest jira = new JiraInstanceManagerRest(baseUrl)


        String jobFile = "com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/srJob.groovy"
        String jobNote = "SPOC Job"
        String jobCron = "0 0 22 ? * SAT"
        String userKey = "JIRAUSER10000"

        assert jira.updateScriptrunnerFile("log.warn(\"test\")", jobFile): "Error creating SR Job File"

        assert jira.installScriptRunner(srLicense, srVersionNumber): "Error installing SR version:" + srVersionNumber
        log.info("\tUsing SR version:" + srVersionNumber)
        sleep(1500)//Wait for sr to detect file changes


        when: "Creating SR job"
        log.info("\tCreating SR Job")
        log.info("\t" * 2 + "Job Note:" + jobNote)
        log.info("\t" * 2 + "File:" + jobFile)
        log.info("\t" * 2 + "Cron:" + jobCron)

        SrJob srJob = jira.createSrJob(jobNote, userKey, jobCron, jobFile)
        log.info("\tCreated job:" + srJob?.id)

        then: "Job should be created and listable"
        srJob
        jira.getSrJobs().find { it.id == srJob.id && it.fieldJobCode.scriptPath == jobFile && it.fieldInterval == jobCron }
        log.info("\t" * 2 + "Job was successfully created and is returned when requested")

        when: "Deleting the job"
        log.info("\tDeleting SR Job:" + srJob.id)
        assert jira.deleteSrJob(srJob.id): "Error deleting job"

        then:
        assert jira.getSrJobs().find { it.id == srJob.id } == null: "Job could still be found after deletion"


        cleanup:
        if (last) {
            //Not sure why a new instance is needed here, deletion/cleanup fails otherwise
            JiraInstanceManagerRest jim = new JiraInstanceManagerRest(baseUrl)

            assert jim.installScriptRunner(srLicense, baseSrVersion): "Error installing SR version:" + baseSrVersion
        }


        where:
        srVersionNumber | last
        "latest"        | false
        "7.13.0"        | false
        "7.0.0"         | false
        "6.55.0"        | true


    }

    def "Test runSpockTest"(String srVersionNumber, boolean last) {
        setup:
        log.info("Testing RunSpockTestV7")
        JiraInstanceManagerRest jira = new JiraInstanceManagerRest(baseUrl)
        File jiraLocalScriptsDir = new File("src/test/groovy/com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts")
        assert jiraLocalScriptsDir.isDirectory()
        File jiraLocalScriptRootDir = new File("src/test/groovy")
        assert jiraLocalScriptRootDir.isDirectory()


        assert jira.installScriptRunner(srLicense, srVersionNumber): "Error installing SR version:" + srVersionNumber
        log.info("\tUsing SR version:" + srVersionNumber)


        log.info("\tUsing test files found in local dir:" + jiraLocalScriptsDir.name)

        //Cleanup already uploaded test scripts to get to a known state
        jiraLocalScriptsDir.eachFileRecurse(FileType.FILES) { scriptFile ->
            String scriptRelativePath = jiraLocalScriptRootDir.relativePath(scriptFile)
            log.debug("\tClearing test file on JIRA server: " + scriptRelativePath)

            assert jira.updateScriptrunnerFile("", scriptRelativePath): "Error clearing script file on remote JIRA server:" + scriptRelativePath
        }

        when: "When running the main test as packageToRun, classToRun and methodToRun"
        log.info("Uploading main package test class")
        assert jira.updateScriptrunnerFile(new File("src/test/groovy/com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/JiraLocalSpockTest.groovy"), "com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/JiraLocalSpockTest.groovy"): "Error updating main spock package file"
        sleep(1500)//Wait for sr to detect file changes
        log.info("\tRunning matching package, class and method tests")
        SpockResult spockPackageOut = jira.runSpockTest("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts")
        SpockResult spockClassOut = jira.runSpockTest("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest")
        SpockResult spockMethodOut = jira.runSpockTest("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest", "A successful test in JiraLocalSpockTest")


        then: "They should all return successful tests"
        spockMethodOut.successfulTests.displayName.join() == "A successful test in JiraLocalSpockTest"
        spockClassOut.successfulTests.displayName.join() == "A successful test in JiraLocalSpockTest"
        spockPackageOut.successfulTests.displayName.join() == "A successful test in JiraLocalSpockTest"

        spockMethodOut.successful
        spockPackageOut.successful
        spockClassOut.successful
        log.info("\tSuccessfully tested running the main package test")

        when: "When adding a Spock test to a sub package"
        log.info("Uploading sup package test class")
        assert jira.updateScriptrunnerFile(new File("src/test/groovy/com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/subPackage/JiraLocalSubSpockTest.groovy"), "com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/subPackage/JiraLocalSubSpockTest.groovy"): "Error updating sub package file"
        sleep(2000) //SR needs sometime to pick up the diff

        log.info("\tRunning the same package, class and method tests")
        spockPackageOut = jira.runSpockTest("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts")
        spockClassOut = jira.runSpockTest("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest")
        spockMethodOut = jira.runSpockTest("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest", "A successful test in JiraLocalSpockTest")


        then: "The new test should be run when running a package test, but not when running the old class and method tests"
        assert spockPackageOut.successfulTests.displayName == ["A successful test in JiraLocalSpockTest", "A successful test in JiraLocalSubSpockTest"]: "The spock package run did not run both the expected tests"
        spockMethodOut.successfulTests.displayName.join() == "A successful test in JiraLocalSpockTest"
        spockClassOut.successfulTests.displayName.join() == "A successful test in JiraLocalSpockTest"
        log.info("\tThe new sub package test class was only run by the package test")


        when: "Adding a failing test to the main package"
        log.info("Uploading failing test class")
        assert jira.updateScriptrunnerFile(new File("src/test/groovy/com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/JiraLocalFailedSpockTest.groovy"), "com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/JiraLocalFailedSpockTest.groovy"): "Error updating failing test file"
        sleep(2000) //SR needs sometime to pick up the diff

        log.info("\tRunning the same package, class and method tests")
        spockPackageOut = jira.runSpockTest("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts")
        spockClassOut = jira.runSpockTest("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest")
        spockMethodOut = jira.runSpockTest("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest", "A successful test in JiraLocalSpockTest")


        then: "Two successful tests and one failed should be returned from the package test run"
        assert spockPackageOut.successfulTests.displayName == ["A successful test in JiraLocalSpockTest", "A successful test in JiraLocalSubSpockTest"]: "The spock package run did not run both the expected successful tests"
        assert spockPackageOut.failedTests.displayName == ["A failed test in JiraLocalSpockTest"]: "The spock package run did not return the failed methods"
        assert spockMethodOut.successfulTests.displayName.join() == "A successful test in JiraLocalSpockTest"
        assert spockClassOut.successfulTests.displayName.join() == "A successful test in JiraLocalSpockTest"
        log.info("\tSuccessfully detected failing test")

        cleanup:
        if (last) {
            assert jira.installScriptRunner(srLicense, baseSrVersion): "Error installing SR version:" + baseSrVersion
        }


        where:
        srVersionNumber | last
        "latest"        | false
        "7.13.0"        | false
        "6.58.1"        | false
        "6.55.0"        | true

    }

    def "Test runSpockTestV6"(String srVersionNumber, boolean last) {


        setup:
        log.info("Testing RunSpockTestV6")
        JiraInstanceManagerRest jira = new JiraInstanceManagerRest(baseUrl)
        File jiraLocalScriptsDir = new File("src/test/groovy/com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts")
        assert jiraLocalScriptsDir.isDirectory()
        File jiraLocalScriptRootDir = new File("src/test/groovy")
        assert jiraLocalScriptRootDir.isDirectory()

        assert jira.installScriptRunner(srLicense, srVersionNumber): "Error installing SR version:" + srVersionNumber
        log.info("\tUsing SR version:" + srVersionNumber)

        log.info("\tUsing test files found in local dir:" + jiraLocalScriptsDir.name)

        //Cleanup already uploaded test scripts to get to a known state
        jiraLocalScriptsDir.eachFileRecurse(FileType.FILES) { scriptFile ->
            String scriptRelativePath = jiraLocalScriptRootDir.relativePath(scriptFile)
            log.debug("\tClearing test file on JIRA server: " + scriptRelativePath)

            assert jira.updateScriptrunnerFile("", scriptRelativePath): "Error clearing script file on remote JIRA server:" + scriptRelativePath
        }

        when: "When running the main test as packageToRun, classToRun and methodToRun"
        log.info("Uploading main package test class")
        assert jira.updateScriptrunnerFile(new File("src/test/groovy/com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/JiraLocalSpockTest.groovy"), "com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/JiraLocalSpockTest.groovy"): "Error updating main spock package file"
        sleep(1500)//Wait for SR to detect file changes

        log.info("\tRunning matching package, class and method tests")
        LazyMap spockPackageOut = jira.runSpockTestV6("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts")
        LazyMap spockClassOut = jira.runSpockTestV6("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest")
        LazyMap spockMethodOut = jira.runSpockTestV6("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest", "A successful test in JiraLocalSpockTest")

        then: "They should all succeed and return the same data"
        spockPackageOut.passedMethods == ["A successful test in JiraLocalSpockTest"]
        spockPackageOut.failedMethods == [:]
        spockPackageOut.ignoredMethods == []
        spockClassOut == spockMethodOut && spockClassOut == spockClassOut
        log.info("\tSuccessfully tested running the main package test")

        when: "When adding a Spock test to a sub package"
        log.info("Uploading sup package test class")
        assert jira.updateScriptrunnerFile(new File("src/test/groovy/com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/subPackage/JiraLocalSubSpockTest.groovy"), "com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/subPackage/JiraLocalSubSpockTest.groovy"): "Error updating sub package file"
        sleep(1500)//Wait for SR to detect file changes

        log.info("\tRunning the same package, class and method tests")
        spockPackageOut = jira.runSpockTestV6("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts")
        spockClassOut = jira.runSpockTestV6("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest")
        spockMethodOut = jira.runSpockTestV6("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest", "A successful test in JiraLocalSpockTest")

        then: "The new test should be run when running a package test, but not when running the old class and method tests"
        assert spockPackageOut.passedMethods == ["A successful test in JiraLocalSpockTest", "A successful test in JiraLocalSubSpockTest"]: "The spock package run did not run both the expected tests"
        assert spockPackageOut.failedMethods == [:]: "The spock package run returned failed methods"
        assert spockPackageOut.ignoredMethods == []: "The spock package run returned ignored methods"
        assert spockPackageOut != spockClassOut: "The spock class run not be the same as the package run"
        assert spockClassOut == spockMethodOut: "The spock class and method run should be the same"
        log.info("\tThe new sub package test class was only run by the package test")


        when: "Adding a failing test to the main package"
        log.info("Uploading failing test class")
        assert jira.updateScriptrunnerFile(new File("src/test/groovy/com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/JiraLocalFailedSpockTest.groovy"), "com/eficode/atlassian/jiraInstanceManager/jiraLocalScripts/JiraLocalFailedSpockTest.groovy"): "Error updating failing test file"
        sleep(1500)//Wait for SR to detect file changes

        log.info("\tRunning the same package, class and method tests")
        spockPackageOut = jira.runSpockTestV6("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts")
        spockClassOut = jira.runSpockTestV6("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest")
        spockMethodOut = jira.runSpockTestV6("com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts", "JiraLocalSpockTest", "A successful test in JiraLocalSpockTest")


        then: "Two successful tests and one failed should be returned"
        assert spockPackageOut.passedMethods == ["A successful test in JiraLocalSpockTest", "A successful test in JiraLocalSubSpockTest"]: "The spock package run did not run both the expected successful tests"
        assert spockPackageOut.failedMethods.toString().contains("A failed test in JiraLocalSpockTest"): "The spock package run did not return the failed methods"
        assert spockPackageOut.ignoredMethods == []: "The spock package run returned ignored methods"

        assert spockClassOut == spockMethodOut: "The spock class and method run should be the same"


        cleanup:
        if (last) {
            assert jira.installScriptRunner(srLicense, baseSrVersion): "Error installing SR version:" + baseSrVersion
        }


        where:
        srVersionNumber | last
        "6.54.0"        | false
        "6.50.0"        | false
        "6.40.0"        | false
        "6.30.0"        | true
    }


    def "Test createJsmProjectWithSampleData"() {

        setup:
        JiraInstanceManagerRest jiraR = new JiraInstanceManagerRest(baseUrl)
        String projectKey = jiraR.getAvailableProjectKey("SPOC")

        when:
        ProjectBean projectBean = jiraR.createJsmProjectWithSampleData(projectKey, projectKey)

        then:
        projectBean != null
    }

    def "Simple getProjectsTest"() {

        setup:
        JiraInstanceManagerRest jiraR = new JiraInstanceManagerRest(baseUrl)

        expect:
        !jiraR.getProjects().empty
        jiraR.getProjects().every { it instanceof ProjectBean }

    }

    def "Test getting arbitrary user cookies"() {


        setup: "Create sample data"

        String spocUsername = "spoc_" + System.currentTimeSeconds()
        String spocUserKey = '""'
        JiraInstanceManagerRest jiraRest = getJiraInstanceManagerRest()


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
        JiraInstanceManagerRest jira = new JiraInstanceManagerRest(baseUrl)
        UnirestInstance spocInstance = Unirest.spawnInstance()
        spocInstance.config().defaultBaseUrl(baseUrl)


        log.info("\tCreating spoc user:" + spocUsername)


        Map createUserResult = jiraRest.executeLocalScriptFile(userCrudScript.replace(["CREATE_USER": "true", "DELETE_USER": "false", "USERNAME_INPUT": spocUsername]))
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
        Map deleteUserResult = jiraRest.executeLocalScriptFile(userCrudScript.replace(["CREATE_USER": "false", "DELETE_USER": "true", "SPOC_USER_KEY": spocUserKey]))
        assert deleteUserResult.success
        log.info("\tSpoc user was deleted")


    }


    def "Test CRUD of SR Local DB Resource"() {

        setup:
        JiraInstanceManagerRest jiraR = getJiraInstanceManagerRest()

        //Cleanup pre-existing pool
        String preExistingPool = jiraR.getLocalDbResourceId("spoc-pool")
        if (preExistingPool) {
            jiraR.deleteLocalDbResourceId(preExistingPool)
        }


        when: "Instantiate JiraInstanceManager"

        then:
        assert jiraR.createLocalDbResource("spoc-pool"): "Error creating Local DB Resource"
        log.info("DB Resource created")

        when: "Querying for the ID of the pool"
        String poolId = jiraR.getLocalDbResourceId("spoc-pool")
        log.info("getLocalDbResourceId returned:" + poolId)

        then:
        poolId
        poolId.size() > 10
        log.info("getLocalDbResourceId: appears to function")

        then:
        assert jiraR.deleteLocalDbResourceId(poolId)
        assert jiraR.getLocalDbResourceId("spoc-pool") == null
        log.info("deleteLocalDbResourceId: appears to function")

    }


    def "Test creation of SR Rest endpoint"() {
        setup: "Instantiate JiraInstanceManager"

        JiraInstanceManagerRest jira = getJiraInstanceManagerRest()


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
        boolean createResult = jira.createScriptedRestEndpoint("", endpointScriptBody, "A description")
        log.info("\tGetting response from the new endpoint")
        HttpResponse queryNewEndpointResponse = Unirest.get("/rest/scriptrunner/latest/custom/spocTest").asJson()
        log.info("\t\tGot:" + queryNewEndpointResponse.body.toString())

        then: "The createResult should be true, and the endpoint should return the expected status and data"
        createResult
        queryNewEndpointResponse.status == 200
        queryNewEndpointResponse.body.getObject().get("status") == "working"

        then: "Deleting the new endpoint should succeed"
        jira.deleteScriptedRestEndpointId(jira.getScriptedRestEndpointId("spocTest"))


        when: "Creating a rest endpoint with script file"
        log.info("Creating scripted rest endpoint with Script file")
        assert jira.updateScriptrunnerFile(endpointScriptBody, "spocTestEndpoint.groovy"): "Error creating script file"
        log.info("\tCreated script file")

        createResult = jira.createScriptedRestEndpoint("spocTestEndpoint.groovy", "", "A description")
        log.info("\tGetting response from the new endpoint")
        queryNewEndpointResponse = Unirest.get("/rest/scriptrunner/latest/custom/spocTest").asJson()
        log.info("\t\tGot:" + queryNewEndpointResponse.body.toString())

        then: "The createResult should be true, and the endpoint should return the expected status and data"
        createResult
        queryNewEndpointResponse.status == 200
        queryNewEndpointResponse.body.getObject().get("status") == "working"

        then: "Deleting the new endpoint should succeed"
        jira.deleteScriptedRestEndpointId(jira.getScriptedRestEndpointId("spocTest"))


    }

    def "Test creation of Insight sample project"() {

        setup: "Instantiate JiraInstanceManager"

        log.info("Will test creation of Insight sample project")


        JiraInstanceManagerRest jira = new JiraInstanceManagerRest(baseUrl)
        jira.acquireWebSudoCookies()

        String projectName = "Spoc Src FieldSchema"
        String projectKey = jira.getAvailableProjectKey("SSS")

        ArrayList<Integer> preExistingSchemaIds = jira.getInsightSchemas().id

        when: "When creating the project"
        Map resultMap = jira.createInsightProjectWithSampleData(projectName, projectKey)

        then: "Project and schema should be returned"
        assert resultMap.project
        assert resultMap.project.projectKey == projectKey
        assert resultMap.project.projectName == projectName
        assert resultMap.schema
        assert !preExistingSchemaIds.contains(resultMap.schema.id as int)
        assert jira.getProjects(false).find { it.projectId == resultMap.project.projectId }: "getProjects() could not find project"
        log.info("\tFieldSchema and project successfully created")


        cleanup:
        jira.deleteInsightSchema(resultMap.schema.id as int)
        jira.deleteProject(resultMap.project)


    }

    def "Test Import and Export of Insight Object Schemas"() {
        setup: "Instantiate JiraInstanceManager"

        log.info("Will test export and import of insight object schemas")

        JiraInstanceManagerRest jira = getJiraInstanceManagerRest()
        jira.acquireWebSudoCookies()

        String srcSchemaName = "Spoc Src FieldSchema"
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

                ]).asJson().body.getObject().toMap()

        log.info("\tFieldSchema created with status ${sampleSchemaMap.status} and Id: " + sampleSchemaMap.id)
        assert sampleSchemaMap.status == "Ok", "Error creating sample schema"

        when: "Exporting the new sample schema"
        log.info("Exporting the new sample schema")
        Map exportResult = jira.exportInsightSchema(sampleSchemaMap.name as String, sampleSchemaMap.id as String, "anOutput", true)
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
        jira.executeLocalScriptFile(moveFileToImportScriptBody)


        log.info("Importing the newly created export")
        Map importResult = jira.importInsightSchema(exportFile, srcSchemaName + " reImported", srcSchemaKey + "imp", srcSchemaName, true)

        log.info("\tImport result:" + importResult?.result)
        String importSchemaId = importResult.resourceId

        String importFile = importResult.resultData.fileName as String


        then:
        importResult.status == "FINISHED"
        importResult.result == "OK"
        importFile?.contains("anOutput")
        importFile?.endsWith(".zip")


        when: "Making sure getInsightSchemas() finds the schemas"
        ArrayList<ObjectSchemaBean> schemas = jira.getInsightSchemas()

        log.trace("getInsightSchemas() returned schemas:" + schemas.name)

        then: "Should contain both schemas"
        assert schemas.find { it.id == sampleSchemaMap.id && it.objectSchemaKey == sampleSchemaMap.objectSchemaKey }: "Expected API to find source schema (Key: ${sampleSchemaMap.objectSchemaKey}, Id: ${sampleSchemaMap.id} )"
        assert schemas.find { it.objectSchemaKey == srcSchemaKey + "IMP" }

        expect: "Deleting the schema"
        assert jira.deleteInsightSchema(sampleSchemaMap.id as int): "Error deleting schema"
        assert !jira.getInsightSchemas().find { it.id == sampleSchemaMap.id && it.key == sampleSchemaMap.key }: "After schema deletion, API still says the schema exists"


        cleanup:
        log.info("\tFinished testing import and export of object schemas")
        Unirest.delete("/rest/insight/1.0/objectschema/" + sampleSchemaMap.id).cookie(sudoCookies).asEmpty()
        Unirest.delete("/rest/insight/1.0/objectschema/" + importSchemaId).cookie(sudoCookies).asEmpty()

    }


    def "Test Asset Field Crud"() {

        setup: "Instantiate JiraInstanceManager"

        log.info("Will test export and import of insight object schemas")

        JiraInstanceManagerRest jim = getJiraInstanceManagerRest()

        if (!jim.projects.find { it.projectName == "Asset Field Crud" }) {

            jim.createJsmProjectWithSampleData("Asset Field Crud", "ASSFCRUD")
        }

        ArrayList<FieldBean.FieldType> assetFieldTypes = jim.getFieldTypes().findAll { it.key.startsWith("com.riadalabs.jira.plugins.insight") }

        expect:

        assetFieldTypes.each { assetFieldType ->


            String fieldPrefix = System.currentTimeMillis().toString()[-5..-1]


            FieldBean newField = jim.createCustomfield(fieldPrefix + "-" + assetFieldType.name, assetFieldType.searchers.first(), assetFieldType.key, "Field type: ${assetFieldType.name}")

            assert newField.class == AssetFieldBean: "createCustomfield() did not return an AssetFieldBean when creating an asset field"

            AssetFieldBean newAssetField = newField as AssetFieldBean

            assert newAssetField.getConfigSchemeIds().size() == 1: "getConfigSchemeIds() did not return the expected number of schema ids"

            assert newAssetField.deleteCustomField()

        }


    }

    def "Test Field Crud"() {

        setup: "Instantiate JiraInstanceManager"

        log.info("Will test export and import of insight object schemas")

        JiraInstanceManagerRest jim = getJiraInstanceManagerRest()

        if (!jim.projects.find { it.projectName == "Field Crud" }) {
            jim.createJsmProjectWithSampleData("Field Crud", "FIELDCRUD")
        }


        when: "When getting all fields"
        ArrayList<FieldBean> jiraFieldBeans = FieldBean.getFields(jim)


        then: "Custom and system fields should be returned. FieldTypes should be found, JSM CAB field should have been created with global project and issueType scope"
        jiraFieldBeans.any { it.custom }
        jiraFieldBeans.any { it.custom == false }
        jiraFieldBeans.any { it.name == "Key" && it.id == "issuekey" }
        jiraFieldBeans.size() > 10
        FieldBean.FieldType.getFieldTypes(jim).size() > 35
        FieldBean.getFields(jim).id.sort() == jim.getFields().id.sort()
        assert jiraFieldBeans.find { it.name == "CAB" }.getFieldContexts().allProjects == [true]: "Expected CAB field to be applied to all projects"
        assert jiraFieldBeans.find { it.name == "CAB" }.getFieldContexts().allIssueTypes == [true]: "Expected CAB field applied to all issue types"


        expect: "Creation of fields with global/specific issueType/project of all fieldTypes and searchers, to work."
        log.info("Testing creation of fields")
        jim.getFieldTypes().each { fieldType ->
            String fieldTypKey = fieldType.key
            log.info("\tTesting creation of field type:" + fieldTypKey)
            fieldType.searchers.each { searcherKey ->
                log.info("\t" * 2 + "Creating using Searcher:" + searcherKey)
                String fieldPrefix = System.currentTimeMillis().toString()[-5..-1]
                ArrayList<String> projectIds = jim.getProjects().projectId
                projectIds.add(null)
                projectIds.shuffle()
                projectIds = [projectIds.first()]

                log.info("\t" * 3 + "Adding to project:" + (projectIds == [null] ? " Global (all Projects)" : projectIds.join(",")))

                ArrayList<String> issueTypeIds = jim.getIssueTypes().id
                issueTypeIds.add("-1")
                issueTypeIds.shuffle()
                issueTypeIds = [issueTypeIds.first()]
                log.info("\t" * 3 + "Adding to IssueType:" + (issueTypeIds == ["-1"] ? " Global (all issueTypes)" : issueTypeIds.join(",")))


                FieldBean newField = jim.createCustomfield(fieldPrefix + "-" + fieldType.name, searcherKey, fieldTypKey, "Field type: ${fieldType.name}, Project: $projectIds, IssueType: $issueTypeIds", projectIds, issueTypeIds)
                log.info("\t" * 3 + "Created field:" + newField.toString())

                ArrayList<FieldBean.FieldConfigurationContext> fieldConfigContexts = newField.getFieldContexts()
                assert fieldConfigContexts.size() == 1: "Expected new field to only have one config context"
                FieldBean.FieldConfigurationContext fieldConfigContext = fieldConfigContexts.first()
                if (projectIds == [null]) {

                    assert fieldConfigContext.allProjects: "Field ${newField.id} was setup as project-global but field config context  was not set to allProjects=true"
                    assert fieldConfigContext.projects.isEmpty(): "Field ${newField.id} was setup as project-global fieldConfigContext.projects was not empty"
                    //assert newField.getFieldProjects(true).projectId == jim.getProjects().projectId: "Field ${newField.id} was setup as project-global but getFieldProjects(true) did not return all projects"

                } else {
                    assert projectIds.toString() == fieldConfigContext.projects.collect { it.projectId }.toString(): "Field ${newField.id} was setup for specific projects ($projectIds) but fieldConfigContext.projects did not return the expected projects"
                }


                if (issueTypeIds == ["-1"]) {
                    assert fieldConfigContext.allIssueTypes: "Field ${newField.id} was setup as issueType-global but fieldConfigContext.allIssueTypes was not set to false"
                    assert fieldConfigContext.issueTypes.isEmpty(): "Field ${newField.id} was setup as issueType-global fieldConfigContext.issueTypes was not empty"
                } else {
                    assert issueTypeIds.toString() == fieldConfigContext.issueTypes.id.flatten().toString(): "Field ${newField.id} was setup for specific issueTypes ($issueTypeIds) butfieldConfigContext.issueTypes did not return the expected issueTypes"
                }


                assert newField.getFieldType(true).key == fieldType.key: "Field ${newField.id} was suposed to be type ${fieldType.key}, but API returned ${newField.getFieldType(true).key}"
                log.info("\t" * 3 + "Field was successfully created")

                ArrayList<FieldBean.FieldConfigurationContext> configContexts = newField.getFieldContexts()

                assert newField.deleteCustomField(): "Error deleting customfield " + newField.id
                log.info("\t" * 3 + "Field was successfully deleted")

            }
        }


    }


    String beanMapGenerator(Map map) {
        String out = ""

        out = "[\n${map.keySet().collect { it + ':""' }.join(",\n")}\n]"

        return out

    }

    String beanGenerator(Map oneObject) {
        String out = ""

        oneObject.each { fieldName, value ->
            if (value instanceof Map) {
                out += value.getClass().simpleName + " " + fieldName + " = " + beanMapGenerator(value) + "\n"
                // out += value.getClass().simpleName + " " + fieldName + " " + beanGenerator(value) + "\n"
            } else {
                out += value.getClass().simpleName + " " + fieldName + "\n"
            }

        }

        return out
    }


    def "Test JQL"() {

        setup:
        log.info("Testing JQL")
        String projectName = "JQL Test"
        String projectKey = "JQL"
        JiraInstanceManagerRest jira = getJiraInstanceManagerRest()


        ProjectBean projectBean = jira.createJsmProjectWithSampleData(projectName, projectKey)


        when:
        ArrayList<IssueBean> issues = jira.jql("project = $projectKey ORDER BY id")


        then:
        issues.size() > 40
        issues.every { it instanceof IssueBean }
        log.info("\tJQL was tested successfully")


        cleanup:
        jira.deleteProject(projectKey)

    }

}
