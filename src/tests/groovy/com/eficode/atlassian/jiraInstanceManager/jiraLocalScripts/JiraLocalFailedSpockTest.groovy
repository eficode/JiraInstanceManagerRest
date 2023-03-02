package com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts


import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

class JiraLocalFailedSpockTest extends Specification {

    @Shared
    static Logger log = LoggerFactory.getLogger(JiraLocalFailedSpockTest.class)


    def "A failed test in JiraLocalSpockTest"() {

        setup:

        log.info("Running spock test:" + this.specificationContext.currentIteration.name)
        expect:
        false

        cleanup:
        log.info("\tTest finished with exception:" + $spock_feature_throwable)

    }




}
