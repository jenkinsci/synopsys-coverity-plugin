package com.synopsys.integration.jenkins.coverity.actions;

import javax.annotation.CheckForNull;

import hudson.model.Action;

public class IssueReportAction implements Action {
    private final String cimViewUrl;

    public IssueReportAction(final String cimViewUrl) {
        this.cimViewUrl = cimViewUrl;
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "/plugin/synopsys-coverity/icons/synopsys-logo-400px.png";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "See issues in Coverity Connect";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return cimViewUrl;
    }

}
