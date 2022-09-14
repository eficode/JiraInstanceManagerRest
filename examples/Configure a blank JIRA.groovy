import com.eficode.atlassian.jiraInstanceManger.JiraInstanceMangerRest

/**
 *
 * Setups a blank JIRA, installs some apps, created some ScriptRunner resources and runs a scriptrunner script
 *
 * jiraUrl should point at a JIRA that has just started for the first time and nothing else has been done to it
 *
 * Tested with Java 11.0.11, Groovy 3.0.10 and Groovy 2.5.17
 *
 *
 */

String jiraUrl = " http://localhost:8080"
String adminUsername = "admin" //Create an admin user with this username
String adminPassword = "admin" //Create an admin user with this password
//A JIRA license key
String jiraLicense = """
Multiline 
quotes
help
"""
//A scriptrunner license key
String scriptrunnerLicense = """
Multiline 
quotes
help
"""

//Enable trace logging
System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");

JiraInstanceMangerRest instanceManager = new JiraInstanceMangerRest()
instanceManager.adminUsername = adminUsername
instanceManager.adminPassword = adminPassword


//Setup a local H2 database
instanceManager.setupH2Database()
//Configure the basic JIRA application settings
instanceManager.setApplicationProperties(jiraLicense, "JIRA", jiraUrl)

//Install ScriptRunner and REST API Browser from marketplace
instanceManager.installApp("https://marketplace.atlassian.com/download/apps/6820/version/1005740", scriptrunnerLicense)
instanceManager.installApp("https://marketplace.atlassian.com/download/apps/1211542/version/302030") //Rest API Browser



/**
 * Example of Importing an Insight Schema
 * "schema.zip" should be placed in $JIRAHOME/import/insight/
 */
//instanceManager.importInsightSchema("schema.zip", "JR", "JR")

//Create a JSM project with sample data
instanceManager.createJsmProjectWithSampleData("Jira Rest", "JR")


Map filesToUpdate = [
        "../src/customEficodeLibraries/jiraInstanceManger/"        : "customEficodeLibraries/jiraInstanceManger/",
        // "/some/directory/file.groovy"  : "directory/file.groovy"

]
//Upload script files to JIRA
instanceManager.updateScriptrunnerFiles(filesToUpdate)

//Create a scripted rest endpoint referencing a file
//assert instanceManager.createScriptedRestEndpoint("customEficodeLibraries/someDir/aRestEndpoint.groovy")



//Create a ScriptRunner Database resource
assert instanceManager.createLocalDbResource("local")


String addProjectRoleScript = "log.warn(\"Script was run!\")"
assert instanceManager.executeLocalScriptFile(addProjectRoleScript).success






