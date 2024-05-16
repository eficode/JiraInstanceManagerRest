package com.eficode.atlassian.jiraInstanceManager


/*
To Add Xray Reporting:

import app.getxray.xray.junit.customjunitxml.EnhancedLegacyXmlReportGeneratingListener

@Grapes(
        @Grab(group = 'app.getxray', module = 'xray-junit-extensions', version = '0.8.0')
)

EnhancedLegacyXmlReportGeneratingListener xrayListener = new EnhancedLegacyXmlReportGeneratingListener(outputDir.toPath(), printWriter)

launcher.registerTestExecutionListeners(xrayListener)
xrayListener.executionFinished(testPlan)
*/


@Grapes(
        @Grab(group='io.qameta.allure', module='allure-junit5', version='2.27.0')
)

/**
 * For inspiration and inspiration for further imrpovements:
 *
 * https://stackoverflow.com/questions/39111501/whats-the-equivalent-of-org-junit-runner-junitcore-runclasses-in-junit-5
 * https://gist.github.com/danhyun/972c21395f11cde0759565991b08d513
 * https://stackoverflow.com/questions/9062412/generate-xml-files-used-by-junit-reports
 * https://junit.org/junit5/docs/snapshot/user-guide/#junit-platform-reporting
 */


import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import io.qameta.allure.junitplatform.AllureJunitPlatform
import org.junit.platform.launcher.TestPlan
import com.onresolve.scriptrunner.runner.rest.common.CustomEndpointDelegate
import groovy.transform.BaseScript
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.junit.platform.launcher.Launcher
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.listeners.TestExecutionSummary
import org.junit.platform.reporting.open.xml.OpenTestReportGeneratingListener
import org.spockframework.runtime.model.FeatureMetadata


import javax.servlet.http.HttpServletRequest
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response
import org.codehaus.jackson.map.ObjectMapper
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.LogManager

import java.lang.reflect.Method


import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass

@BaseScript CustomEndpointDelegate delegate
//@WithPlugin("com.riadalabs.jira.plugins.insight")


Logger log = LogManager.getLogger("remoteSpec.util.jiraLocal.remoteSpoc") as Logger
Configurator.setLevel(log, Level.ALL)
log.addAppender()

remoteSpock(httpMethod: "POST", groups: ["jira-administrators"]) { MultivaluedMap queryParams, String body, HttpServletRequest request ->


    ObjectMapper objectMapper = new ObjectMapper()

    log.info("Remote Spock triggered")
    String urlSubPath = getAdditionalPath(request)
    log.debug("\tGot url sub path:" + urlSubPath)
    log.debug("\tGot query parameters:" + queryParams)
    log.debug("\tGot body:" + body.take(15) + (body.size() > 15 ? "..." : ""))


    String finalOutput = ""
    if (urlSubPath.startsWith("/spock/class")) {
        log.info("\tRunning spock class")

        Map bodyMap = objectMapper.readValue(body, Map)

        finalOutput = runSpockClass(log, bodyMap.get("className", null) as String, bodyMap.get("methodName", null) as String)

        log.info("Finished running spock class, returning output:\n" + finalOutput)
        return Response.ok(finalOutput, MediaType.TEXT_PLAIN).build()

    } else {
        return Response.status(Response.Status.BAD_REQUEST).entity("Unsupported url sub path:" + urlSubPath).build()
    }


}

static String runSpockClass(Logger logEndpoint, String spockClassName, String spockMethodName = "") {


    logEndpoint.info("Starting Spock test")
    String loggerName = "RemoteSpock-ScriptLog" + System.currentTimeMillis().toString().takeRight(4)
    logEndpoint.debug("\tScript will use logger named:" + loggerName)

    logEndpoint.debug("\tRetrieving Spock class:" + spockClassName)

    Class spockClass = Class.forName(spockClassName)
    logEndpoint.debug("\t\tFound class: " + spockClass.canonicalName)
    Method spockMethod = null

    if (spockMethodName) {
        logEndpoint.debug("\tRetrieving Spock method:" + spockMethodName)
        spockMethod = spockClass.getDeclaredMethods().find {
            FeatureMetadata featureMetadata = it.getAnnotation(FeatureMetadata)
            if (!featureMetadata || featureMetadata.name() != spockMethodName) {
                return false
            }
            return true
        }

        if (!spockMethod) {
            throw new InputMismatchException("Could not find method: ${spockClass.canonicalName}#$spockMethodName")
        }
        logEndpoint.debug("\t\tFound method: " + spockMethod.name + " (compiled name)")
    }

    TestExecutionSummary spockSummary = executeSpockTest(spockClass, spockMethod)

    StringWriter stringWriter = new StringWriter()
    PrintWriter printWriter = new PrintWriter(stringWriter)
    spockSummary.printTo(printWriter)
    spockSummary.printFailuresTo(printWriter)


    String out = stringWriter.dump()

    logEndpoint.info(out)

    return out

}


static TestExecutionSummary executeSpockTest(Class aClass, Method aMethod = null) {

    System.setProperty("junit.platform.reporting.open.xml.enabled", "true")

    LauncherDiscoveryRequest request

    if (aMethod) {
        request = LauncherDiscoveryRequestBuilder.request().selectors(selectMethod(aClass, aMethod)).build()
    } else {
        request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(aClass)).build()
    }


    Launcher launcher = LauncherFactory.create();


    SummaryGeneratingListener sumListener = new SummaryGeneratingListener();
    OpenTestReportGeneratingListener listener = new OpenTestReportGeneratingListener()

    AllureJunitPlatform allureListener = new AllureJunitPlatform()

    launcher.registerTestExecutionListeners(listener)

    launcher.registerTestExecutionListeners(allureListener)
    launcher.registerTestExecutionListeners(sumListener)
    TestPlan testPlan = launcher.discover(request)


    allureListener.testPlanExecutionStarted(testPlan)
    listener.testPlanExecutionStarted(testPlan)
    launcher.execute(request)
    listener.testPlanExecutionFinished(testPlan)

    allureListener.testPlanExecutionFinished(testPlan)


    TestExecutionSummary summary = sumListener.getSummary()

    return summary
}



