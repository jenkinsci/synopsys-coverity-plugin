/*
 * synopsys-coverity
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.io.IOException;

import com.synopsys.integration.coverity.api.rest.View;
import com.synopsys.integration.coverity.api.rest.ViewContents;
import com.synopsys.integration.coverity.api.ws.configuration.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.view.ViewReportWrapper;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.stepworkflow.AbstractSupplyingSubStep;
import com.synopsys.integration.stepworkflow.SubStepResponse;

import hudson.AbortException;

public class GetIssuesInView extends AbstractSupplyingSubStep<ViewReportWrapper> {
    private final ConfigurationServiceWrapper configurationServiceWrapper;
    private final ViewService viewService;
    private final String projectName;
    private final String viewName;
    private final CoverityJenkinsIntLogger logger;

    public GetIssuesInView(final CoverityJenkinsIntLogger logger, final ConfigurationServiceWrapper configurationServiceWrapper, final ViewService viewService, final String projectName, final String viewName) {
        this.logger = logger;
        this.configurationServiceWrapper = configurationServiceWrapper;
        this.viewService = viewService;
        this.projectName = projectName;
        this.viewName = viewName;
    }

    public SubStepResponse<ViewReportWrapper> run() {
        try {
            logger.alwaysLog(String.format("Checking for issues in project \"%s\", view \"%s\".", projectName, viewName));
            final ProjectDataObj project = configurationServiceWrapper.getProjectByExactName(projectName)
                                               .orElseThrow(() -> new AbortException("Coverity Issues could not be retrieved: No project with name " + projectName + " could be found. "
                                                                                         + "It either does not exist or the credentials configured in the Jenkins system configuration are insufficient to access it."));

            final View view = viewService.getViewByExactName(viewName)
                                  .orElseThrow(() -> new AbortException("Coverity Issues could not be retrieved: No view with name " + viewName + " could be found. "
                                                                            + "It either does not exist or the credentials configured in the Jenkins system configuration are insufficient to access it."));

            final ViewContents viewContents = viewService.getViewContents(project, view, 1, 0);
            final String viewReportUrl = viewService.getProjectViewReportUrl(project, view);
            final ViewReportWrapper viewReportWrapper = new ViewReportWrapper(viewContents, viewReportUrl);

            return SubStepResponse.SUCCESS(viewReportWrapper);
        } catch (final IOException | IntegrationException | CovRemoteServiceException_Exception e) {
            return SubStepResponse.FAILURE(e);
        }
    }

}
