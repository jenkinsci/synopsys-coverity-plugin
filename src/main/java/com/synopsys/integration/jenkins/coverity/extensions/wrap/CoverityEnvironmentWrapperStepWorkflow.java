/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.wrap;

import java.io.IOException;
import java.util.List;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityJenkinsStepWorkflow;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.stepworkflow.StepWorkflow;
import com.synopsys.integration.stepworkflow.StepWorkflowResponse;

import hudson.AbortException;
import hudson.scm.ChangeLogSet;
import jenkins.tasks.SimpleBuildWrapper;

public class CoverityEnvironmentWrapperStepWorkflow extends CoverityJenkinsStepWorkflow<Object> {
    public static final String FAILURE_MESSAGE = "Unable to inject Coverity Environment: ";

    private final CoverityWorkflowStepFactory coverityWorkflowStepFactory;
    private final SimpleBuildWrapper.Context context;
    private final String workspaceRemotePath;
    private final String coverityInstanceUrl;
    private final String credentialsId;
    private final String projectName;
    private final String streamName;
    private final String viewName;
    private final Boolean createMissingProjectsAndStreams;
    private final List<ChangeLogSet<?>> changeSets;
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;

    public CoverityEnvironmentWrapperStepWorkflow(JenkinsIntLogger jenkinsIntLogger, JenkinsVersionHelper jenkinsVersionHelper, ThrowingSupplier<WebServiceFactory, CoverityJenkinsAbortException> webServiceFactorySupplier,
        CoverityWorkflowStepFactory coverityWorkflowStepFactory, SimpleBuildWrapper.Context context, String workspaceRemotePath, String coverityInstanceUrl, String credentialsId, String projectName, String streamName, String viewName,
        Boolean createMissingProjectsAndStreams, List<ChangeLogSet<?>> changeSets, ConfigureChangeSetPatterns configureChangeSetPatterns) {
        super(jenkinsIntLogger, jenkinsVersionHelper, webServiceFactorySupplier);
        this.coverityWorkflowStepFactory = coverityWorkflowStepFactory;
        this.context = context;
        this.workspaceRemotePath = workspaceRemotePath;
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.credentialsId = credentialsId;
        this.projectName = projectName;
        this.streamName = streamName;
        this.viewName = viewName;
        this.createMissingProjectsAndStreams = createMissingProjectsAndStreams;
        this.changeSets = changeSets;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
    }

    @Override
    protected StepWorkflow<Object> buildWorkflow() throws AbortException {
        return StepWorkflow
                   .first(coverityWorkflowStepFactory.createStepValidateCoverityInstallation(false))
                   .then(coverityWorkflowStepFactory.createStepCreateAuthenticationKeyFile(workspaceRemotePath, credentialsId, coverityInstanceUrl))
                   .then(coverityWorkflowStepFactory.createStepSetUpCoverityEnvironment(changeSets, configureChangeSetPatterns, workspaceRemotePath, credentialsId, coverityInstanceUrl, projectName, streamName, viewName))
                   .then(coverityWorkflowStepFactory.createStepPopulateEnvVars(context::env))
                   .andSometimes(coverityWorkflowStepFactory.createStepCreateMissingProjectsAndStreams(coverityInstanceUrl, credentialsId, projectName, streamName)).butOnlyIf(createMissingProjectsAndStreams, Boolean.TRUE::equals)
                   .build();
    }

    @Override
    public Boolean perform() throws IOException {
        StepWorkflowResponse<Object> response = runWorkflow();
        try {
            if (!response.wasSuccessful()) {
                throw response.getException();
            }
        } catch (IntegrationException e) {
            logger.debug(null, e);
            throw new AbortException(FAILURE_MESSAGE + e.getMessage());
        } catch (Exception e) {
            throw new IOException(FAILURE_MESSAGE + e.getMessage(), e);
        }

        return response.wasSuccessful();
    }

    @Override
    protected void cleanUp() throws AbortException {
        // The CoverityEnvironmentWrapper needs to clean up later than other workflows, so we create a Disposer and attach it to the context instead.
    }

}
