package com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts


import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Ignore("Only run inside of JIRA container")
class JiraLocalFailedSpockTest extends Specification {

    @Shared
    static Logger log = LoggerFactory.getLogger(JiraLocalFailedSpockTest.class)


    def "A failed test in JiraLocalSpockTest"() {

        setup:

        log.warn("Running spock test:" + this.specificationContext.currentIteration.name)
        expect:
        false

        cleanup:
        log.warn("\tTest finished with exception:" + $spock_feature_throwable)

    }


}
