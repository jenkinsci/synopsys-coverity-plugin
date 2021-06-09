/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.actions;

import javax.annotation.CheckForNull;

import com.synopsys.integration.coverity.ws.view.ViewReportWrapper;

import hudson.model.Action;

public class IssueReportAction implements Action {
    private final int defectCount;
    private final String cimViewUrl;

    public static IssueReportAction fromViewReportWrapper(ViewReportWrapper viewReportWrapper) {
        return new IssueReportAction(viewReportWrapper.getViewContents().getTotalRows().intValue(), viewReportWrapper.getViewReportUrl());
    }

    public IssueReportAction(int defectCount, String cimViewUrl) {
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
