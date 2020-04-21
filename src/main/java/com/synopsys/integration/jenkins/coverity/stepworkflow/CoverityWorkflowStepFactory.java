/**
 * synopsys-polaris
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
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.net.MalformedURLException;
import java.util.List;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.function.ThrowingConsumer;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.stepworkflow.SubStep;
import com.synopsys.integration.stepworkflow.jenkins.RemoteSubStep;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogSet;
import jenkins.model.GlobalConfiguration;

public class CoverityWorkflowStepFactory {
    private final EnvVars envVars;
    private final Launcher launcher;
    private final TaskListener listener;
    private final FilePath workspace;
    private final String coverityServerUrl;

    // These fields are lazily initialized; inside this class: use getOrCreate...() to get these values
    private IntEnvironmentVariables _intEnvironmentVariables = null;
    private CoverityJenkinsIntLogger _logger = null;
    private WebServiceFactory _webServiceFactory = null;

    public CoverityWorkflowStepFactory(final FilePath workspace, final EnvVars envVars, final Launcher launcher, final TaskListener listener, final String coverityServerUrl) {
        this.workspace = workspace;
        this.envVars = envVars;
        this.launcher = launcher;
        this.listener = listener;
        this.coverityServerUrl = coverityServerUrl;
    }

    public CreateMissingProjectsAndStreams createStepCreateMissingProjectsAndStreams(final String projectName, final String streamName) throws AbortException {
        final CoverityJenkinsIntLogger logger = getOrCreateLogger();
        final WebServiceFactory webServiceFactory = getOrCreateWebServiceFactory();
        final ConfigurationServiceWrapper configurationServiceWrapper;
        try {
            configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
        } catch (final MalformedURLException malformedURLException) {
            throw new AbortException("Coverity cannot be executed: '" + coverityServerUrl + WebServiceFactory.CONFIGURATION_SERVICE_V9_WSDL + "' is a malformed URL");
        }

        return new CreateMissingProjectsAndStreams(logger, configurationServiceWrapper, projectName, streamName);
    }

    // TODO: Remove Jenkins extension object?
    public GetCoverityCommands createStepGetCoverityCommands(final CoverityRunConfiguration coverityRunConfiguration) {
        final CoverityJenkinsIntLogger logger = getOrCreateLogger();
        final IntEnvironmentVariables intEnvironmentVariables = getOrCreateEnvironmentVariables();

        return new GetCoverityCommands(logger, intEnvironmentVariables, coverityRunConfiguration);
    }

    public GetIssuesInView createStepGetIssuesInView(final String projectName, final String viewName) throws AbortException {
        final CoverityJenkinsIntLogger logger = getOrCreateLogger();
        final WebServiceFactory webServiceFactory = getOrCreateWebServiceFactory();
        final ConfigurationServiceWrapper configurationServiceWrapper;
        try {
            configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
        } catch (final MalformedURLException malformedURLException) {
            throw new AbortException("Coverity cannot be executed: '" + coverityServerUrl + WebServiceFactory.CONFIGURATION_SERVICE_V9_WSDL + "' is a malformed URL");
        }
        final ViewService viewService = webServiceFactory.createViewService();

        return new GetIssuesInView(logger, configurationServiceWrapper, viewService, projectName, viewName);
    }

    // TODO: Remove Jenkins extension object?
    public ProcessChangeLogSets createStepProcessChangeLogSets(final List<ChangeLogSet<?>> changeLogSets, final ConfigureChangeSetPatterns configureChangeSetPatterns) {
        final CoverityJenkinsIntLogger logger = getOrCreateLogger();

        return new ProcessChangeLogSets(logger, changeLogSets, configureChangeSetPatterns);
    }

    // TODO: Remove Jenkins extension object?
    public RunCoverityCommands createStepRunCoverityCommands(final OnCommandFailure onCommandFailure) {
        final CoverityJenkinsIntLogger logger = getOrCreateLogger();
        final IntEnvironmentVariables intEnvironmentVariables = getOrCreateEnvironmentVariables();
        final VirtualChannel virtualChannel = launcher.getChannel();
        final String remoteWorkingDirectory = workspace.getRemote();

        return new RunCoverityCommands(logger, intEnvironmentVariables, remoteWorkingDirectory, onCommandFailure, virtualChannel);
    }

    public SetUpCoverityEnvironment createStepSetUpCoverityEnvironment(final String projectName, final String streamName, final String viewName, final String intermediateDirectory) {
        final CoverityJenkinsIntLogger logger = getOrCreateLogger();
        final IntEnvironmentVariables intEnvironmentVariables = getOrCreateEnvironmentVariables();

        return new SetUpCoverityEnvironment(logger, intEnvironmentVariables, coverityServerUrl, projectName, streamName, viewName, intermediateDirectory);
    }

    public RemoteSubStep<Boolean> createStepValidateCoverityInstallation(final Boolean validateversion, final String coverityToolHome) {
        final CoverityJenkinsIntLogger logger = getOrCreateLogger();
        final VirtualChannel virtualChannel = launcher.getChannel();

        final ValidateCoverityInstallation validateCoverityInstallation = new ValidateCoverityInstallation(logger, validateversion, coverityToolHome);
        return RemoteSubStep.of(virtualChannel, validateCoverityInstallation);
    }

    public SubStep<Integer, Object> createStepWithConsumer(final ThrowingConsumer<Integer, RuntimeException> consumer) {
        return SubStep.ofConsumer(consumer);
    }

    public CoverityJenkinsIntLogger getOrCreateLogger() {
        final IntEnvironmentVariables intEnvironmentVariables = getOrCreateEnvironmentVariables();
        if (_logger == null) {
            _logger = CoverityJenkinsIntLogger.initializeLogger(listener, intEnvironmentVariables);
        }
        return _logger;
    }

    public IntEnvironmentVariables getOrCreateEnvironmentVariables() {
        if (_intEnvironmentVariables == null) {
            _intEnvironmentVariables = new IntEnvironmentVariables(false);
            _intEnvironmentVariables.putAll(envVars);
        }
        return _intEnvironmentVariables;
    }

    public WebServiceFactory getOrCreateWebServiceFactory() throws AbortException {
        final JenkinsIntLogger logger = getOrCreateLogger();
        if (_webServiceFactory == null) {
            final CoverityGlobalConfig coverityGlobalConfig = GlobalConfiguration.all().get(CoverityGlobalConfig.class);
            if (coverityGlobalConfig == null) {
                throw new AbortException("Coverity cannot be executed: No Coverity global configuration detected in the Jenkins system configuration.");
            }
            final List<CoverityConnectInstance> coverityConnectInstances = coverityGlobalConfig.getCoverityConnectInstances();
            if (coverityConnectInstances.isEmpty()) {
                throw new AbortException("Coverity cannot be executed: No Coverity connect instances are configured in the Jenkins system configuration.");
            }
            final CoverityConnectInstance coverityConnectInstance = coverityConnectInstances.stream()
                                                                        .filter(instance -> instance.getUrl().equals(coverityServerUrl))
                                                                        .findFirst()
                                                                        .orElseThrow(() -> new AbortException(
                                                                            "Coverity cannot be executed: No Coverity conect instance with the url '" + coverityServerUrl + "' could be  found in the Jenkins system configuration."));

            final CoverityServerConfig coverityServerConfig = coverityConnectInstance.getCoverityServerConfig();
            _webServiceFactory = coverityServerConfig.createWebServiceFactory(logger);
        }
        return _webServiceFactory;
    }

}
