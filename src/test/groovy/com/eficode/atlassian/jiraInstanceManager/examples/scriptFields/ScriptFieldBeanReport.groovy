package com.eficode.atlassian.jiraInstanceManager.examples.scriptFields

import com.eficode.atlassian.jiraInstanceManager.beans.FieldBean
import com.eficode.atlassian.jiraInstanceManager.beans.ScriptFieldBean
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovy.transform.stc.FromString
import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Matcher
import java.util.regex.Pattern

class ScriptFieldBeanReport {


    ScriptFieldBean scriptFieldBean
    static Logger log = LoggerFactory.getLogger(ScriptFieldBeanReport.class)

    static final long executionTimeWarn = 1500
    static final long executionTimeError = 3000
    static final int scriptSimilarWarnPercent = 75

    static final textSimilarity = new LevenshteinDistance()

    static Closure closureTest


    ScriptFieldBeanReport(ScriptFieldBean scriptField) {
        this.scriptFieldBean = scriptField
    }

    static Map<String, String> toCsv(ArrayList<ScriptFieldBeanReport> reports) {

        String csvHeader = "Field Name, Field ID, May Break Index Due To JQL, May Break Index Due To Assets,Uses Stattable Searcher,Uses Natural Searcher,Max execution time,Average execution time, Failures,Rendered in projects, Number of executions < 2 Sec apart\n"


        String configReport = csvHeader + reports.collect { it.toCsv() }.join("\n")

        return [
                "configReport"    : configReport,
                "scriptSimilarity": getScriptSimilarityMarkdownBlock(reports.scriptFieldBean, "csv")
        ]
    }

    static String toJiraMarkDown(ArrayList<ScriptFieldBeanReport> reports) {


        return reports.collect { it.toJiraMarkdown() }.join("\n") + getScriptSimilarityMarkdownBlock(reports.scriptFieldBean) + footer

    }

    static String getScriptSimilarityMarkdownBlock(ArrayList<ScriptFieldBean> fieldBeans, String format = "jira") {

        assert format in ["jira", "csv"]

        //Ignores fields that have no scriptBody
        Map<ScriptFieldBean, String> fieldBodyMap = [:]
        fieldBeans.each {
            String scriptBody = it.scriptBody
            if (scriptBody != "") {
                fieldBodyMap.put(it, scriptBody)
            }

        }

        Map<ScriptFieldBean, Map<ScriptFieldBean, String>> similarityMatrix = [:]

        fieldBodyMap.each { firstField, firstFieldBody ->
            Map<ScriptFieldBean, String> bodySimilarity = [:]

            fieldBodyMap.each { secondField, secondFieldBody ->


                try {
                    if (firstFieldBody == "" && secondFieldBody == "") {
                        bodySimilarity.put(secondField, "null")
                    } else {
                        Integer charsThatDiff = textSimilarity.apply(firstFieldBody, secondFieldBody)
                        double similarityPercent = 100 - ((charsThatDiff / [firstFieldBody.length(), secondFieldBody.length()].max())) * 100l
                        similarityPercent = similarityPercent.trunc(1)

                        if (format == "jira") {
                            bodySimilarity.put(secondField, similarityPercent >= scriptSimilarWarnPercent ? "{color:red}$similarityPercent%{color}" : similarityPercent + "%")
                        } else {
                            bodySimilarity.put(secondField, similarityPercent + "%")
                        }


                    }

                } catch (ignored) {
                    log.warn("Error determening Script Similarity between ${firstField} and $secondField")
                    if (format == "jira") {
                        bodySimilarity.put(secondField, "{color:red}Error{color}")
                    } else {
                        bodySimilarity.put(secondField, "Error")
                    }
                }


            }

            if (bodySimilarity.size() > 0) {
                similarityMatrix.put(firstField, bodySimilarity)
            }


        }

        String tableBody
        if (format == "jira") {
            tableBody = "|| ||" + similarityMatrix.keySet().customFieldId.sort().collect { it + "||" }.join() + "\n"
        } else {
            tableBody = " , " + similarityMatrix.keySet().customFieldId.sort().join(",") + "\n"
        }

        similarityMatrix.sort { it.key.customFieldId }.each { rowFieldBean, columnBeans ->


            if (format == "jira") {
                tableBody += "||" + rowFieldBean.customFieldId + "|" + columnBeans.sort { it.key.customFieldId }.values().collect { it + "|" }.join() + "\n"
            } else {
                tableBody += rowFieldBean.customFieldId + "," + columnBeans.sort { it.key.customFieldId }.values().join(",") + "\n"
            }

        }

        String header = "\\\\\n" +
                "----\n" +
                "h2. Script Similarity Matrix\n" +
                "The table below aims to help you find scripts that are very similar and that could perhaps be combined\n" +
                "The Row and Colum headers represents CustomFiled ID´s while the cell is a percentage of how similar the scripts are between the fields\n"

        return (format == "jira" ? header : "") + tableBody

    }

    static String getFooter() {

        return "\\\\\n" +
                "----\n" +
                "h2. Background/Information\n" +
                "[May Break Index due to JQL - JQL Searches in Script Fields |https://docs.adaptavist.com/sr4js/latest/features/script-fields/script-field-tips]\n" +
                "[Uses Stattable Searcher |https://docs.adaptavist.com/sr4js/latest/features/script-fields]\n" +
                "[Uses Natural Searcher |https://marketplace.atlassian.com/apps/1210967/natural-searchers-for-jira?hosting=datacenter&tab=overview] Similar to Stattable searchers but uses third party App\n"

    }


    String toCsv() {
        return [scriptFieldBean.name, scriptFieldBean.customFieldId, mayBreakIndexDueToJql(), mayBreakIndexDueToAssets(), usesStattableSearcher(), usesNaturalSearcher(), getExecutionHistoryBlock("csv")].join(",")
    }

    String toJiraMarkdown() {

        return "h2. $scriptFieldBean\n" +
                "*May Break Index due to JQL:*\t" + mayBreakIndexDueToJql() + " " + (mayBreakIndexDueToJql() ? "(!)" : "(/)") + "\n" +
                "*May Break Index due to Assets:*\t" + mayBreakIndexDueToAssets() + " " + (mayBreakIndexDueToAssets() ? "(!)" : "(/)") + "\n" +
                "*Uses Stattable Searcher:*\t" + usesStattableSearcher() + " " + (usesStattableSearcher() ? "(!)" : "(/)") + "\n" +
                "*Uses Natural Searcher:*\t" + usesNaturalSearcher() + " " + (usesNaturalSearcher() ? "(!)" : "(/)") + "\n" +
                executionHistoryBlock + "\n"
    }


    String getExecutionHistoryBlock(String format = "jira") {

        assert format == "jira" || format == "csv"


        ArrayList<ScriptFieldBean.ScriptFieldExecution> fieldExecutions = scriptFieldBean.getExecutions()

        String blockBody = format == "jira" ? "h3. Field Execution History (last ${fieldExecutions.size()})\n" : ""
        if (fieldExecutions.empty) {
            blockBody += format == "jira" ? "*Field has never been executed:* (!)\n" : "Never Executed,Never Executed,Never Executed,Never Executed,Never Executed"
            return blockBody
        }

        long maxExecutionTime = (fieldExecutions.millisecondsTaken.toList() + (fieldExecutions.cpuTime.collect { it / 1000000 }) as List).max() as Long
        long avgExecutionTime = (fieldExecutions.millisecondsTaken.toList() + (fieldExecutions.cpuTime.collect { it / 1000000 }) as List).average() as Long
        int failures = fieldExecutions.exception.findAll { it != null }.size()

        ArrayList<String> projectKeys = fieldExecutions.issueKey.collect { it.toString().substring(0, it.toString().indexOf("-")) }.unique()

        ArrayList<Long> executionTimestamps = fieldExecutions.created.sort()
        ArrayList<Long> timeBetweenExecutions = []
        executionTimestamps.eachWithIndex { long timeStamp, int i ->
            if (fieldExecutions.created.size() > i && i > 0) {
                long lastExecution = executionTimestamps[i - 1]
                timeBetweenExecutions += timeStamp - lastExecution
            }
        }
        long nrOfCloseExecutions = timeBetweenExecutions.findAll { it < 2000 }.size()
        int percentOfCloseExecutions = ((nrOfCloseExecutions / fieldExecutions.size()) * 100).toInteger()


        if (format == "jira") {
            blockBody += "*Max execution time:* " + maxExecutionTime + "ms " + (maxExecutionTime > executionTimeError ? "(x)" : (maxExecutionTime > executionTimeWarn ? "(!)" : "(/)")) + "\n"
            blockBody += "*Average execution time:* " + avgExecutionTime + "ms " + (avgExecutionTime > executionTimeError ? "(x)" : (avgExecutionTime > executionTimeWarn ? "(!)" : "(/)")) + "\n"
            blockBody += "*Failures:* " + failures + " (${((failures / fieldExecutions.size()) * 100).toInteger()}%) " + (failures ? "(!)" : "(/)") + "\n"
            blockBody += "*Rendered in projects:* " + projectKeys.join(", ") + "\n"
            blockBody += "*Number of executions < 2 Sec apart:* " + nrOfCloseExecutions + " (${percentOfCloseExecutions}%)" + (percentOfCloseExecutions > 30 ? "(!)" : "(/)")

        } else {
            blockBody += [maxExecutionTime, avgExecutionTime, failures, projectKeys.join("; "), nrOfCloseExecutions].join(",")
        }
        return blockBody

    }

    //https://docs.adaptavist.com/sr4js/latest/features/script-fields
    boolean usesStattableSearcher() {

        return scriptFieldBean.searcherName?.containsIgnoreCase("Stattable")
    }

    boolean usesNaturalSearcher() {

        return scriptFieldBean.searcherName?.containsIgnoreCase("(natural)")
    }


    /**
     * When a Full Re-index is run in JIRA, script fields get executed without a user context/anonymously,
     * so if a ScriptField queries Assets/Insight during it´s execution that will likely fail and give a "PermissionInsightException: Anonymous User"
     *
     * One solution is to in the ScriptField-script set logged in user if currentUser == null:
     *
     *  if (!jiraAuth.loggedInUser) {
     log.info("\tChanging current user")
     jiraAuth.setLoggedInUser(userManager.getUserByName("admin"))
     log.info("Current user now is:" + jiraAuth.loggedInUser)
     }
     * @return
     */
    boolean mayBreakIndexDueToAssets() {

        String scriptBody = scriptFieldBean.getScriptBody()
        ArrayList<String> assetsIndicators = [
                "Assets.",
                "InsightManagerForScriptrunner",
                "import com.eficode.atlassian.insightmanager.InsightManagerForScriptrunner",
                "import com.riadalabs.jira.plugins.insight",
                "objectFacade",
                "loadObjectAttributeBean",
                "objectBean"
        ]
        ArrayList<String> autIndicators = [".setLoggedInUser("]

        if (scriptBody == "") {
            return false
        }

        if (assetsIndicators.any { assetsIndicator -> scriptBody.containsIgnoreCase(assetsIndicator) }) {
            log.info(scriptFieldBean.toString() + " looks to be using Assets/Insight, checking if it also appears to handle \"currentUser\" correctly")

            if (autIndicators.any { authIndicator -> scriptBody.containsIgnoreCase(authIndicator) }) {
                log.info("\t" + scriptFieldBean.toString() + " appears to handle \"currentUser\" correctly")
                return false
            } else {
                log.warn("\t" + scriptFieldBean.toString() + " appears to use Assets/Insight but not setting currentUser")
                return true
            }
        }

        return false

    }

    /**
     * Checks if scriptBody contains JQL related methods/references
     * This can break the index: https://docs.adaptavist.com/sr4js/latest/features/script-fields/script-field-tips
     * @return True if JQL might be used by scriptField
     */
    boolean mayBreakIndexDueToJql() {

        String scriptBody = scriptFieldBean.getScriptBody()

        if (scriptBody == "") {
            return false
        }

        ArrayList<String> jqlIndicators = ["JqlQueryParser", "SearchProvider", "SearchQuery", "JqlQueryBuilder", "SearchService", "Issues.count(", "Issues.search("]
        if (jqlIndicators.any { jqlIndicator -> scriptBody.containsIgnoreCase(jqlIndicator) }) {
            log.info(scriptFieldBean.toString() + " looks to be using JQL, checking if it handles the Index Correctly")

            if (scriptBody.containsIgnoreCase("isIndexAvailable")) {
                log.info("\tField appears to correctly check if index is available")
                return false
            } else {
                log.warn("\t$scriptFieldBean does not appear to check if index is available")
                return true
            }


        } else {
            return false
        }

    }

    static File findFile(String fileName, String rootPath = ".") {
        File rootDir = new File(rootPath)


        File matchingFile
        rootDir.eachDirRecurse { dir ->
            matchingFile ?: dir.eachFileMatch(~/$fileName/) {
                matchingFile = it
            }
        }

        return matchingFile
    }

    static Integer getScriptBodyLastImportIndex(String body) {
        Integer lastImport = body.split("\n").findLastIndexOf {
            it.toString().trim().matches(/import \w*\.\w*\..*/)
        }
        return lastImport
    }

    //Try to determine the final "return" statement from script
    static String getScriptReturnStatement(String body) {
        ArrayList<String> rows = body.trim().split("\n")
        ArrayList<String> cleanRows = rows
        cleanRows.removeAll { it.toString().startsWith("//") || it == "" }

        String last = cleanRows.last()

        if (last ==~ /^.*return.*/ && rows.findAll { it == last }.size() == 1) {
            return last
        } else {
            return null
        }
    }

    static String getAssetPatchImports() {
        return """
        import com.atlassian.jira.component.ComponentAccessor
        import com.atlassian.jira.security.JiraAuthenticationContext
        import com.atlassian.jira.user.ApplicationUser
        import com.atlassian.jira.user.util.UserManager
        
        """.replaceAll(Pattern.compile(/^ */, Pattern.MULTILINE), "")


    }

    static String getAssetPatchBody(String serviceUserName) {
        return """

        JiraAuthenticationContext jiraAuth = ComponentAccessor.getJiraAuthenticationContext()
        UserManager userManager = ComponentAccessor.getUserManager()
        
        ApplicationUser initialUser = jiraAuth.getLoggedInUser()
        ApplicationUser serviceUser = userManager.getUserByName("$serviceUserName")
        jiraAuth.setLoggedInUser(serviceUser)

        """.replaceAll(Pattern.compile(/^ */, Pattern.MULTILINE), "")
    }

    static String getAssetPatchFooter() {
        return "jiraAuth.setLoggedInUser(initialUser)"
    }

    String getAssetPatchedBody(String assetsServiceUser) {
        log.info("Attempting to patch script body for ${this.scriptFieldBean.toString()}, fixing asset issues")

        assert mayBreakIndexDueToAssets(): this.toString() + " does not appear to need patching for Asset issues"
        log.debug("\tConfirmed field needs patching")
        String returnStatement = getScriptReturnStatement(this.scriptFieldBean.scriptBody)

        if (!returnStatement) {
            log.warn("\t" + scriptFieldBean + " needs to be patched due to Assets usage, but cant be patched automatically")
            log.warn("\t" * 2 + " Could not determine return statement for $scriptFieldBean")

            return null

        } else {
            log.info("\t" + scriptFieldBean + " patching for Asset usage")
            String scriptBody = scriptFieldBean.scriptBody

            int lastImportLine = getScriptBodyLastImportIndex(scriptBody)

            ArrayList<String> imports = scriptBody.split("\n").toList().subList(0,lastImportLine  + 1)
            imports.addAll(assetPatchImports.split("\n"))



            ArrayList<String> body = scriptBody.split("\n").toList()[getScriptBodyLastImportIndex(scriptBody) + 1..-1]
            body = getAssetPatchBody(assetsServiceUser).split("\n") + body
            String patchedScriptBody = imports.join("\n") + body.join("\n")
            assert patchedScriptBody.count(returnStatement) == 1 : "Error patching ${scriptFieldBean.toString()}, could not determine final return statement. "
            patchedScriptBody = patchedScriptBody.replace(returnStatement, getAssetPatchFooter() + "\n" + returnStatement)


            return  patchedScriptBody

        }


    }


    File getPatchedScriptFile(String assetsServiceUser) {
        //File getPatchedScriptFile(String assetsServiceUser, @ClosureParams(value =  FirstParam) Closure<String> customPatcher = null) {


        log.info("Patching " + scriptFieldBean)

        File scriptFieldsDir = new File("scriptFields/" + scriptFieldBean + "/")
        scriptFieldsDir.mkdirs()
        log.info("\tWill store output in:" + scriptFieldsDir.absolutePath)

        String originalScriptBody = scriptFieldBean.scriptBody
        File originalScriptFile = new File(scriptFieldsDir, scriptFieldBean.toString() + "-Original.groovy")
        originalScriptFile.text = originalScriptBody
        log.info("\t" * 2 + "Created backup of original script file:" + originalScriptFile.path)

        String patchedScriptBody = originalScriptBody


        /*
        Map<Pattern, String> replace = [
                (Pattern.compile(/^(.*)/, Pattern.MULTILINE)) : ""
                //(Pattern.compile(/(.*(?<returnVar>F) =.*)/, Pattern.MULTILINE)) : ""

        ]

        replace.each {pattern, replacement ->
            Matcher matcher = pattern.matcher(patchedScriptBody)
            if (matcher.size() > 0) {

                def test2 = matcher.group(0)
                ArrayList<String> namedGroups = pattern.namedGroups().keySet()
                namedGroups.each {groupName ->
                    String test = matcher.group(0)

                    ""
                }

            }else {
                throw new InputMismatchException("Regex pattern did not match:" + pattern.toString())
            }
        }

         */

        //replaceAll(Pattern.compile(/^ */, Pattern.MULTILINE), "")


        boolean needsAssetPatch = mayBreakIndexDueToAssets()
        boolean needsJqlPatch = mayBreakIndexDueToJql()

        if (needsAssetPatch) {

            String returnStatement = getScriptReturnStatement(patchedScriptBody)

            if (!returnStatement) {
                log.info("\t" + scriptFieldBean + " needs to be patched due to Assets usage, but cant be patched automatically")
                patchedScriptBody = "//COULD NOT BE AUTOMATICALLY PATCHED FOR ASSET PROBLEMS\n" * 2 + patchedScriptBody

            } else {
                log.info("\t" + scriptFieldBean + " patching for Asset usage")

                patchedScriptBody = assetPatchImports + patchedScriptBody
                ArrayList<String> imports = patchedScriptBody.split("\n").toList().subList(0, getScriptBodyLastImportIndex(patchedScriptBody) + 1)
                ArrayList<String> body = patchedScriptBody.split("\n").toList()[getScriptBodyLastImportIndex(patchedScriptBody) + 1..-1]
                body = getAssetPatchBody(assetsServiceUser).split("\n") + body
                patchedScriptBody = imports.join("\n") + body.join("\n")
                patchedScriptBody = patchedScriptBody.replace(returnStatement, getAssetPatchFooter() + "\n" + returnStatement)

            }

        }

        if (needsJqlPatch) {

            log.warn("\t" + scriptFieldBean + " needs to be patched due to JQL usage, but cant be patched automatically")
            patchedScriptBody = "//COULD NOT BE AUTOMATICALLY PATCHED FOR JQL PROBLEMS\n" * 2 + patchedScriptBody
        }

        if (needsJqlPatch || needsAssetPatch) {
            File patchedScriptFile = new File(scriptFieldsDir, scriptFieldBean.toString() + "-Patched.groovy")
            patchedScriptFile.text = patchedScriptBody
            log.info("\tCreated patched script file: " + patchedScriptFile.path)
            return patchedScriptFile
        } else {
            log.info(scriptFieldBean.toString() + " does not need to be patched")
            return null
        }

    }

}