package com.eficode.atlassian.jiraInstanceManager.examples.scriptFields

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import com.eficode.atlassian.jiraInstanceManager.beans.FieldBean
import com.eficode.atlassian.jiraInstanceManager.beans.ScriptFieldBean
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Will Backup the script body of all script fields, regardless if
 * they are inline or file based.
 *
 * If file based the original script name wont be preserved.
 *
 * A JSON representation of the fields config will also be saved
 */

Logger log = LoggerFactory.getLogger("script.field.backuper")
JiraInstanceManagerRest jim = new JiraInstanceManagerRest(System.getenv("restUser"), System.getenv("restPw"), System.getenv("restHost"))

log.info("Backing up ScriptFields from:" + jim.baseUrl)

File backupDir =  new File("scriptFieldsBackup/" )
backupDir.mkdirs()

log.info("\tWill store scripts in:" + backupDir.canonicalPath)


jim.getScriptFields().each {scriptField ->
    File scriptBodyFile = new File(backupDir, scriptField.toString() + ".groovy")
    scriptBodyFile.text = scriptField.scriptBody
    log.info("\tCreated backup:" + scriptField.name)


    File scriptFieldJsonFile = new File(backupDir, scriptField.toString()  + " - original ScriptBean.groovy")
    scriptFieldJsonFile.text = scriptField.toJson()
    log.info("\tCreated backup:" + scriptField.name)


}

assert jim.getScriptFields().every{remoteField ->

    File backedUpBody = new File(backupDir, remoteField.toString() + ".groovy")

    backedUpBody.text == remoteField.scriptBody
} : "Error verifying ScriptFieldBackup"