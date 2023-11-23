package com.eficode.atlassian.jiraInstanceManager

import com.eficode.devstack.container.impl.GroovyContainer
import spock.lang.Shared
import spock.lang.Specification

class CrossGroovyVersionSpec extends Specification{


    @Shared
    String dockerRemoteHost = "https://docker.domain.se:2376"
    @Shared
    String dockerCertPath = "~/.docker/"


    def setupSpec() {

        dockerRemoteHost = ""
        dockerCertPath = ""

    }

    def "testar"() {

        when:
        GroovyContainer container = setupContainer("3.0.11", "", "", "")


        then:
        container.running

        cleanup:
        container.stopAndRemoveContainer()



    }



    GroovyContainer setupContainer(String groovyHostVersion, String repoPath, String repoBranch, String buildProfile) {

        String mvnUrl = "https://dlcdn.apache.org/maven/maven-3/3.9.0/binaries/apache-maven-3.9.0-bin.tar.gz"
        String mavenNameAndVersion = mvnUrl.substring(mvnUrl.lastIndexOf("/") + 1,mvnUrl.lastIndexOf("-bin.tar.gz")) //ex: apache-maven-3.9.0

        String engineM2Cache = "/opt/m2" //Docker engine local m2 cache

        //If local docker engine, user current users .m2 directory
        if (!dockerRemoteHost) {
            String userHome = System.getProperty("user.home")
            engineM2Cache = userHome + "/.m2"

        }


        GroovyContainer container = new GroovyContainer(dockerRemoteHost, dockerCertPath)
        container.setGroovyVersion(groovyHostVersion)
        container.containerName = "Building-JiraInst"
        container.stopAndRemoveContainer()
        //container.customEnvVar = ["PATH=/opt/apache-maven-3.9.0/bin:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"]
        container.prepareCustomEnvVar(["PATH=/opt/$mavenNameAndVersion/bin:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin".toString()])
        container.prepareBindMount(engineM2Cache, "/home/groovy/.m2", true)


        container.createSleepyContainer()
        container.startContainer()


        String mvnInstallScript = ""+
        "wget -q $mvnUrl && " +
        "tar -xvf ${mvnUrl.substring(mvnUrl.lastIndexOf("/") + 1)} && " +
        "mv $mavenNameAndVersion /opt/ && echo Status:\$?"

        /*
        String updateProfile = ""+
        "cd && " +
        "echo \"M2_HOME='/opt/$mavenNameAndVersion'\" >> .bashrc && " +
        "echo 'PATH=\"\$M2_HOME/bin:\$PATH\"' >> .bashrc  && " +
        "echo \"export PATH\" >> .bashrc"

         */






        ArrayList<String> cmdOut = container.runBashCommandInContainer(mvnInstallScript, 60000, "root")
        assert cmdOut.contains("Status:0") : "Error installing maven"
        /*cmdOut = container.runBashCommandInContainer(updateProfile, 60000, "root")
        assert cmdOut.isEmpty() : "Error setting up maven in path variables"
        cmdOut = container.runBashCommandInContainer(updateProfile, 60000, "groovy")
        assert cmdOut.isEmpty() : "Error setting up maven in path variables"

         */


        cmdOut = container.runBashCommandInContainer("source .bashrc &&  mvn --version" )

        return container

    }

}
