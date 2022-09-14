# Jira Instance Manager Rest

Jira Instance Manager Rest (JR) is a Groovy library which can perform several administrative tasks solely through the REST API.

## Breaking Changes

* 1.1.0: 
  * Almost all static methods in JiraInstanceManagerRest have been removed in order to better facilitate working with multiple JIRA instances and in cooperation with other libraries using Unirest library.
  * Fixing the unfortunate spelling mistake "JiraInstanceManagrRest" in favor for "JiraInstanceManagerRest"

## Background
Jira Instance Manager Rest (JR) was created specifically for making automatic testing of JIRA customisations easier. The goal was that JR should be able to take a blank JIRA and configure any settings and customizations necessary to then run a full suite of automated tests.

If you give JR a blank JIRA, it can help you:

1. Setup a local H2 database
2. Create an admin account, disable e-mail, install a JIRA license, set BaseURL
3. Create new projects (with or without sample data)
4. Install Apps from Atlassian Marketplace and their licenses
5. Aquire webSudo cookies
6. Use ScriptRunners "Switch User" functionality to act as a different user
7. Create ScriptRunner customisations such as REST-endpoints, DB-resources
8. Run Groovy-scripts or Spoc-tests with ScriptRunner
9. Import/Export Insight Schemas


## What Jira Instance Manager Rest IS NOT

JR is **NOT FOR PRODUCTION USE**, JR relies heavily on private and undocumented APIs, these can change without note.
JR comes with Spoc tests that cover many of the functionalities, but they do not cover all use cases in all situations.

**Never rely on JR functioning in an unsupervised situation, especially in production**


## What Jira Instance Manager Rest If Great For

### Stop using "Golden Images"

Are you using golden images of JIRA instances/databases for testing but find them cumbersome to keep up to date?
Why not script the entire deployment?

### Got a heavily customised workload in JIRA?

Do you have a workload in JIRA that depends heavily on scripted customisations?
With docker and SR you can automate the setup of the workload and then run Spoc/Junit tests on it to confirm any changes have the expected outcomes.


## Adding JR as a dependency

JR packages are published to a separate branch in the JR repository called '[packages](https://github.com/eficode/JiraInstanceManagerRest/tree/packages/repository/com/eficode/atlassian/jirainstancemanager)'. **Check this branch for the most up to date version number**

### Maven (POM)
```XML
<dependencies>
   <dependency>
       <groupId>com.eficode.atlassian</groupId>
       <artifactId>jirainstancemanager</artifactId>
       <version>1.1.0-SNAPSHOT</version>
   </dependency>
</dependencies>
...
<repositories>            
    <repository>
           <id>github-jiraManagerRest</id>
           <url>https://github.com/eficode/JiraInstanceManagerRest/raw/packages/repository/</url>
       </repository>
</repositories>
```


### Groovy (Grape)

```Groovy
@GrabResolver(name = 'github', root = 'https://github.com/eficode/JiraInstanceManagerRest/raw/packages/repository/')
@Grab(group = 'com.eficode.atlassian', module = 'jirainstancemanager', version = '1.1.0-SNAPSHOT')

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest

JiraInstanceManagerRest instanceManager = new JiraInstanceManagerRest("http://jira.domain.com")
```