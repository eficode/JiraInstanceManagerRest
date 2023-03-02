package com.eficode.atlassian.jiraInstanceManager.jiraLocalScripts.subPackage


import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

class JiraLocalSubSpockTest extends Specification {

    @Shared
    static Logger log = LoggerFactory.getLogger(JiraLocalSubSpockTest.class)


    def "A successful test in JiraLocalSubSpockTest"() {

        setup:
        log.info("Running spock test:" + this.specificationContext.currentIteration.name)
        expect:
        true

        cleanup:
        log.info("\tTest finished with exception:" + $spock_feature_throwable)

    }



}
