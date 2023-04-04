package com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts


import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

class JiraLocalSpockTest extends Specification {

    @Shared
    static Logger log = LoggerFactory.getLogger(JiraLocalSpockTest.class)


    def "A successful test in JiraLocalSpockTest"() {

        setup:

        log.warn("Running spock test:" + this.specificationContext.currentIteration.name)
        expect:
        true

        cleanup:
        log.warn("\tTest finished with exception:" + $spock_feature_throwable)

    }



}
