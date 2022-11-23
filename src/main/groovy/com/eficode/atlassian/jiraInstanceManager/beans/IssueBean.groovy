package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

class IssueBean {
    String expand
    String id
    String self
    String key
    Map fields = [
            issuetype:"",
            timespent:"",
            project:"",
            fixVersions:"",
            aggregatetimespent:"",
            resolution:"",
            resolutiondate:"",
            workratio:"",
            lastViewed:"",
            watches:"",
            created:"",
            priority:"",
            labels:"",
            timeestimate:"",
            aggregatetimeoriginalestimate:"",
            versions:"",
            issuelinks:"",
            assignee:"",
            updated:"",
            status:"",
            components:"",
            timeoriginalestimate:"",
            description:"",
            aggregatetimeestimate:"",
            summary:"",
            creator:"",
            subtasks:"",
            reporter:"",
            aggregateprogress:"",
            environment:"",
            duedate:"",
            progress:"",
            votes:""
    ]

    static final ObjectMapper objectMapper = new ObjectMapper()


    static ArrayList<IssueBean> fromArray(ArrayList<Map> rawMaps) {


        ArrayList<IssueBean> issueBeans = objectMapper.convertValue(rawMaps, new TypeReference<ArrayList<IssueBean>>(){})
        issueBeans.each { issue ->
            issue.fields.removeAll {it.key.startsWith("customfield_") && (it.value == null || it.value.isEmpty() )}
        }

        return issueBeans
    }


    static IssueBean fromMap(Map rawMap) {

        IssueBean issueBean = objectMapper.convertValue(rawMap, IssueBean)
        issueBean.fields.removeAll {it.key.startsWith("customfield_") && (it.value == null || it.value.isEmpty() )}

        return issueBean
    }
}
