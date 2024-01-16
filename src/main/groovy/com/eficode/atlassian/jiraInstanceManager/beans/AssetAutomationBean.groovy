package com.eficode.atlassian.jiraInstanceManager.beans

import com.fasterxml.jackson.annotation.JsonProperty


class AssetAutomationBean {


    public Integer id
    public String name
    public String description
    public int schemaId
    public String actorUserKey
    public def disabled

    ArrayList<Event> events
    ArrayList<ConditionsAndActions> conditionsAndActions


    public String created
    public String updated
    public String lastTimeOfAction




    class ConditionsAndActions {

        public int id
        public String name
        ArrayList<Condition> conditions = []
        ArrayList<Action> actions = []

    }

    class Action {

        public int id
        public String name
        public ActionType typeId
        public String data
        public String minTimeBetweenActions



    }
    class Condition {

        public int id
        public String name
        public String condition

    }

    class Event {
        public Integer id
        public String name
        public EventType typeId
        public String iql
        public String cron

    }

    enum ActionType {

        @JsonProperty("com.riadalabs.jira.plugins.insight.services.automation.action.AutomationRuleGroovyScriptAction")
        GroovyScript("Execute Groovy script", 'com.riadalabs.jira.plugins.insight.services.automation.action.AutomationRuleGroovyScriptAction'),
        @JsonProperty("com.riadalabs.jira.plugins.insight.services.automation.action.AutomationRuleHttpRequestAction")
        HttpRequest ("Http Request", "com.riadalabs.jira.plugins.insight.services.automation.action.AutomationRuleHttpRequestAction")


        public String name
        public String type

        ActionType(final String actionName, final String actionType) {
            this.name = actionName
            this.type = actionType
        }

    }

    enum EventType {
        InsightObjectAttachmentAddedEvent,
        InsightObjectAttachmentDeletedEvent,
        InsightObjectCommentAddedEvent,
        InsightObjectCommentDeletedEvent,
        InsightObjectCommentEditedEvent,
        InsightObjectCreatedEvent,
        InsightObjectDeletedEvent,
        InsightObjectMovedEvent,
        InsightObjectUpdatedEvent,
        InsightAutomationScheduleEvent
    }
}
