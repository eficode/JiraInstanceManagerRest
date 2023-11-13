import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.user.util.UserManager

JiraAuthenticationContext jiraAuth = ComponentAccessor.getJiraAuthenticationContext()
UserManager userManager = ComponentAccessor.getUserManager()

ApplicationUser initialUser = jiraAuth.getLoggedInUser()
ApplicationUser serviceUser = userManager.getUserByName("SERVICE_USER")
jiraAuth.setLoggedInUser(serviceUser)
"MAIN_SCRIPT_BODY"

jiraAuth.setLoggedInUser(initialUser)






