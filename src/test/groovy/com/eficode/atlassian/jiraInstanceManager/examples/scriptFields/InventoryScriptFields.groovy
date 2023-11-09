package com.eficode.atlassian.jiraInstanceManager.examples.scriptFields

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import com.eficode.atlassian.jiraInstanceManager.beans.ScriptFieldBean
import groovy.transform.Field

import java.lang.reflect.Method

//TODO Get performance statics, check scripts that sleep for a long time and a looping while loop
//TODO check if field hasnt been executed in X time
//TODO Check if applied globally

JiraInstanceManagerRest jim = new JiraInstanceManagerRest(System.getenv("restUser"), System.getenv("restPw"), System.getenv("restHost"))
//jim.setProxy("127.0.0.1", 8081)
//jim.verifySsl = false


double start = System.currentTimeMillis()
ArrayList<ScriptFieldBean> scriptFieldBeans = jim.getScriptFields()

//scriptFieldBeans = scriptFieldBeans.findAll {it.customFieldId in [11403]}

ArrayList<ScriptFieldBeanReport> scriptFieldsReports = scriptFieldBeans.collect { new ScriptFieldBeanReport(it) }
double stop = System.currentTimeMillis()


scriptFieldsReports.find {it.scriptFieldBean.customFieldId == 11400}.getPatchedScriptFile("SERVICE")
//scriptFieldsReports.findAll {it.mayBreakIndexDueToAssets()}.collect {it.getPatchedScriptFile("SERVICE")}
//File patchedFiled = scriptFieldsReports.first().getPatchedScriptFile("test")
return

ScriptFieldBeanReport.toCsv(scriptFieldsReports).each {
    File outFile = new File("target/" + it.key + ".csv")
    outFile.text = it.value
    ScriptFieldBeanReport.log.info("Created report file:" + outFile.absolutePath)
}
//scriptFieldsReports.collect {it.toJiraMarkdown()}.join("\n")

println("Duration: " + (stop - start).div(1000).toDouble().trunc(1) + "s")

