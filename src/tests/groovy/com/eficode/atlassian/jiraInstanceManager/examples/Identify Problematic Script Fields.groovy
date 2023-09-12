// https://mvnrepository.com/artifact/org.apache.commons/commons-text
@Grapes(
        @Grab(group = 'org.apache.commons', module = 'commons-text', version = '1.10.0')
)

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import com.eficode.atlassian.jiraInstanceManager.beans.FieldBean
import com.eficode.atlassian.jiraInstanceManager.beans.ScriptFieldBean
import com.eficode.atlassian.jiraInstanceManager.beans.ScriptFieldBean.ScriptFieldExecution
import org.apache.commons.text.similarity.JaccardDistance
import org.apache.commons.text.similarity.JaccardSimilarity
import org.apache.commons.text.similarity.JaroWinklerDistance
import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.apache.commons.text.similarity.LevenshteinDistance
import org.apache.commons.text.similarity.SimilarityScoreFrom
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.spockframework.runtime.condition.EditOperation
import org.spockframework.runtime.condition.EditPathRenderer

//TODO Get performance statics, check scripts that sleep for a long time and a looping while loop
//TODO check if field hasnt been executed in X time
//TODO check if field has been executed less than X times in $timeSpan
//TODO Check if applied globally


JiraInstanceManagerRest jim = new JiraInstanceManagerRest("http://jira.localhost:8080")
jim.setProxy("127.0.0.1", 8081)

jim.verifySsl = false

double start = System.currentTimeMillis()
ArrayList<ScriptFieldBean> scriptFieldBeans = jim.getScriptFields()
ArrayList<ScriptFieldBeanReport> scriptFieldsReports = scriptFieldBeans.collect { new ScriptFieldBeanReport(it) }
double stop = System.currentTimeMillis()



String report = ScriptFieldBeanReport.toJiraMarkDown(scriptFieldsReports)
//scriptFieldsReports.collect {it.toJiraMarkdown()}.join("\n")
println(report)
println("Duration: " + (stop - start).div(1000).toDouble().trunc(1) + "s")

class ScriptFieldBeanReport {


    ScriptFieldBean scriptFieldBean
    static Logger log = LoggerFactory.getLogger(ScriptFieldBeanReport.class)

    static final long executionTimeWarn = 1500
    static final long executionTimeError = 3000
    static final int scriptSimilarWarnPercent = 75

    static final textSimilarity = new LevenshteinDistance()
    //static final textSimilarity = new JaccardSimilarity()


    ScriptFieldBeanReport(ScriptFieldBean scriptField) {
        this.scriptFieldBean = scriptField
    }

    static String toJiraMarkDown(ArrayList<ScriptFieldBeanReport> reports) {


        return reports.collect { it.toJiraMarkdown() }.join("\n") + getScriptSimilarityBlock(reports.scriptFieldBean) + footer

    }

    static String getScriptSimilarityBlock(ArrayList<ScriptFieldBean> fieldBeans) {

        //Ignores fields that have no scriptBody

        Map<ScriptFieldBean, String> fieldBodyMap = [:]
        fieldBeans.each {
            String scriptBody = it.scriptBody
            if( scriptBody != "") {
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



                        bodySimilarity.put(secondField, similarityPercent >= scriptSimilarWarnPercent ? "{color:red}$similarityPercent%{color}" : similarityPercent + "%")
                    }

                } catch (ignored) {
                    log.warn("Error determening Script Similarity between ${firstField} and $secondField")
                    bodySimilarity.put(secondField, "{color:red}Error{color}")
                }


            }

            if (bodySimilarity.size() > 0) {
                similarityMatrix.put(firstField, bodySimilarity)
            }


        }

        String tableBody = "|| ||" + similarityMatrix.keySet().customFieldId.sort().collect { it + "||" }.join() + "\n"

        similarityMatrix.sort { it.key.customFieldId }.each { rowFieldBean, columnBeans ->


            tableBody += "||" + rowFieldBean.customFieldId + "|" + columnBeans.sort { it.key.customFieldId }.values().collect { it + "|" }.join() + "\n"
        }

        String header = "\\\\\n" +
                "----\n" +
                "h2. Script Similarity Matrix\n" +
                "The table below aims to help you find scripts that are very similar and that could perhaps be combined\n" +
                "The Row and Colum headers represents CustomFiled ID´s while the cell is a percentage of how similar the scripts are between the fields\n"

        return header + tableBody

    }

    static String getFooter() {

        return "\\\\\n" +
                "----\n" +
                "h2. Background/Information\n" +
                "[May Lock Index - JQL Searches in Script Fields |https://docs.adaptavist.com/sr4js/latest/features/script-fields/script-field-tips]\n" +
                "[Uses Stattable Searcher |https://docs.adaptavist.com/sr4js/latest/features/script-fields]\n" +
                "[Uses Natural Searcher |https://marketplace.atlassian.com/apps/1210967/natural-searchers-for-jira?hosting=datacenter&tab=overview] Similar to Stattable searchers but uses third party App\n"

    }

    String toJiraMarkdown() {

        return "h2. $scriptFieldBean\n" +
                "*May cause index lockup:*\t" + mayCauseIndexLock() + " " + (mayCauseIndexLock() ? "(!)" : "(/)") + "\n" +
                "*Uses Stattable Searcher:*\t" + usesStattableSearcher() + " " + (usesStattableSearcher() ? "(!)" : "(/)") + "\n" +
                "*Uses Natural Searcher:*\t" + usesNaturalSearcher() + " " + (usesNaturalSearcher() ? "(!)" : "(/)") + "\n" +
                executionHistoryBlock + "\n"
    }


    String getExecutionHistoryBlock() {

        ArrayList<ScriptFieldExecution> fieldExecutions = scriptFieldBean.getExecutions()

        String blockBody = "h3. Field Execution History (last ${fieldExecutions.size()})\n"
        if (fieldExecutions.empty) {
            blockBody += "*Field has never been executed:* (!)\n"
            return blockBody
        }

        long maxExecutionTime = (fieldExecutions.millisecondsTaken.toList() + (fieldExecutions.cpuTime.collect { it / 1000000 }) as List).max() as Long
        long avgExecutionTime = (fieldExecutions.millisecondsTaken.toList() + (fieldExecutions.cpuTime.collect { it / 1000000 }) as List).average() as Long
        int failures = fieldExecutions.exception.findAll { it != null }.size()

        ArrayList<String> projectKeys = fieldExecutions.payload.issue.collect { it.toString().substring(0, it.toString().indexOf("-")) }.unique()

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


        blockBody += "*Max execution time:* " + maxExecutionTime + "ms " + (maxExecutionTime > executionTimeError ? "(x)" : (maxExecutionTime > executionTimeWarn ? "(!)" : "(/)")) + "\n"
        blockBody += "*Average execution time:* " + avgExecutionTime + "ms " + (avgExecutionTime > executionTimeError ? "(x)" : (avgExecutionTime > executionTimeWarn ? "(!)" : "(/)")) + "\n"
        blockBody += "*Failures:* " + failures + " (${((failures / fieldExecutions.size()) * 100).toInteger()}%) " + (failures ? "(!)" : "(/)") + "\n"
        blockBody += "*Rendered in projects:* " + projectKeys.join(", ") + "\n"
        blockBody += "*Number of executions < 2 Sec apart:* " + nrOfCloseExecutions + " (${percentOfCloseExecutions}%)" + (percentOfCloseExecutions > 30 ? "(!)" : "(/)")
        return blockBody

    }

    //https://docs.adaptavist.com/sr4js/latest/features/script-fields
    boolean usesStattableSearcher() {

        return scriptFieldBean.searcherName?.containsIgnoreCase("Stattable")
    }

    boolean usesNaturalSearcher() {

        return scriptFieldBean.searcherName?.containsIgnoreCase("(natural)")
    }


    //https://docs.adaptavist.com/sr4js/latest/features/script-fields/script-field-tips
    boolean mayCauseIndexLock() {

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

}

