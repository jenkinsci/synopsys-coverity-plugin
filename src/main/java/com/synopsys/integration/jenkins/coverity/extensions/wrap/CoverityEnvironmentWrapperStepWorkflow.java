/**
 * synopsys-coverity
 *
 * Copyright (c) 2020 Synopsys, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.jenkins.coverity.extensions.wrap;

import java.io.IOException;
import java.util.List;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsStepWorkflow;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
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
    private final String projectName;
    private final String streamName;
    private final String viewName;
    private final Boolean createMissingProjectsAndStreams;
    private final List<ChangeLogSet<?>> changeSets;
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;

    public CoverityEnvironmentWrapperStepWorkflow(final JenkinsIntLogger jenkinsIntLogger, final ThrowingSupplier<WebServiceFactory, CoverityJenkinsAbortException> webServiceFactorySupplier,
        final CoverityWorkflowStepFactory coverityWorkflowStepFactory,
        final SimpleBuildWrapper.Context context, final String workspaceRemotePath, final String coverityInstanceUrl, final String projectName, final String streamName, final String viewName, final Boolean createMissingProjectsAndStreams,
        final List<ChangeLogSet<?>> changeSets, final ConfigureChangeSetPatterns configureChangeSetPatterns) {
        super(jenkinsIntLogger, webServiceFactorySupplier);
        this.coverityWorkflowStepFactory = coverityWorkflowStepFactory;
        this.context = context;
        this.workspaceRemotePath = workspaceRemotePath;
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.projectName = projectName;
        this.streamName = streamName;
        this.viewName = viewName;
        this.createMissingProjectsAndStreams = createMissingProjectsAndStreams;
        this.changeSets = changeSets;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
    }

    protected StepWorkflow<Object> buildWorkflow() throws AbortException {
        return StepWorkflow
                   .first(coverityWorkflowStepFactory.createStepValidateCoverityInstallation(false))
                   .then(coverityWorkflowStepFactory.createStepProcessChangeLogSets(changeSets, configureChangeSetPatterns))
                   .then(coverityWorkflowStepFactory.createStepSetUpCoverityEnvironment(workspaceRemotePath, coverityInstanceUrl, projectName, streamName, viewName))
                   .then(coverityWorkflowStepFactory.createStepPopulateEnvVars(context::env))
                   .andSometimes(coverityWorkflowStepFactory.createStepCreateMissingProjectsAndStreams(coverityInstanceUrl, projectName, streamName)).butOnlyIf(createMissingProjectsAndStreams, Boolean.TRUE::equals)
                   .build();
    }

    public Boolean perform() throws IOException {
        final StepWorkflowResponse<Object> response = runWorkflow();
        try {
            if (!response.wasSuccessful()) {
                throw response.getException();
            }
        } catch (final IntegrationException e) {
            logger.debug(null, e);
            throw new AbortException(FAILURE_MESSAGE + e.getMessage());
        } catch (final Exception e) {
            throw new IOException(FAILURE_MESSAGE + e.getMessage(), e);
        }

        return response.wasSuccessful();
    }

}
