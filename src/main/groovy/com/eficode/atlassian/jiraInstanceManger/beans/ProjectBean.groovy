package com.eficode.atlassian.jiraInstanceManger.beans

import com.fasterxml.jackson.databind.ObjectMapper

class ProjectBean {
    String returnUrl
    Integer projectId
    String projectKey
    String projectName

    static final ObjectMapper objectMapper = new ObjectMapper()

    ProjectBean(){}

    static ProjectBean fromMap(Map rawMap) {
        return objectMapper.convertValue(rawMap, ProjectBean.class)
    }

    /*
    ProjectBean(Map rawMap) {
        returnUrl = rawMap.returnUrl
        projectId = rawMap.projectId
        projectKey = rawMap.projectKey
        projectName = rawMap.projectName

    }

     */


    boolean deleteProject() {

        return deleteProject(projectId)
    }
}