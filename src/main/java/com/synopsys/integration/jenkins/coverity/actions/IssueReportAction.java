/*
 * synopsys-coverity
 *
 * Copyright (c) 2024 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.actions;

import javax.annotation.CheckForNull;

import hudson.model.Action;

public class IssueReportAction implements Action {
    private final int defectCount;
    private final String cimViewUrl;

    public IssueReportAction(final int defectCount, final String cimViewUrl) {
        this.defectCount = defectCount;
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
        return "See " + defectCount + " issues in Coverity Connect";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return cimViewUrl;
    }

}
