package com.eficode.atlassian.jiraInstanceManager.beans

import kong.unirest.HttpResponse

class AssetFieldBean extends FieldBean {


    /*
    ArrayList<Long> getConfigSchemeIds() {

        HttpResponse<Map> rawResponse = jiraInstance.unirest.get("/rest/assets/1.0/customfield/default/configscheme/" + numericId)
                .cookie(jiraInstance.acquireWebSudoCookies())
                .asObject(Map)

        assert rawResponse.status == 200 : "Error config schemeIDs for assets field:" + id
        assert rawResponse.body.containsKey("configurationSchemeIds")

        return rawResponse.body.configurationSchemeIds as ArrayList<Long>

    }

     */



}
