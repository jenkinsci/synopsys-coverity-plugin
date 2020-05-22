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
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.stepworkflow.SubStep;
import com.synopsys.integration.stepworkflow.jenkins.RemoteSubStep;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogSet;
import jenkins.model.GlobalConfiguration;

public class CoverityWorkflowStepFactory {
    private final EnvVars envVars;
    private final Node node;
    private final Launcher launcher;
    private final TaskListener listener;
    // These fields are lazily initialized; inside this class: use the suppliers that guarantee an initialized value
    private IntEnvironmentVariables _intEnvironmentVariables = null;
    private final Supplier<IntEnvironmentVariables> initializedIntEnvrionmentVariables = this::getOrCreateEnvironmentVariables;
    private CoverityJenkinsIntLogger _logger = null;
    private final Supplier<CoverityJenkinsIntLogger> initializedLogger = this::getOrCreateLogger;
    private VirtualChannel _virtualChannel = null;
    private final ThrowingSupplier<VirtualChannel, CoverityJenkinsAbortException> initializedVirtualChannel = this::getOrCreateVirtualChannel;

    public CoverityWorkflowStepFactory(final EnvVars envVars, final Node node, final Launcher launcher, final TaskListener listener) {
        this.envVars = envVars;
        this.node = node;
        this.launcher = launcher;
        this.listener = listener;
    }

    public CreateMissingProjectsAndStreams createStepCreateMissingProjectsAndStreams(final String coverityServerUrl, final String projectName, final String streamName) throws CoverityJenkinsAbortException {
        final WebServiceFactory webServiceFactory = getWebServiceFactoryFromUrl(coverityServerUrl);
        final ConfigurationServiceWrapper configurationServiceWrapper;
        try {
            configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
        } catch (final MalformedURLException malformedURLException) {
            throw CoverityJenkinsAbortException.fromMalformedUrlException(coverityServerUrl + WebServiceFactory.CONFIGURATION_SERVICE_V9_WSDL, malformedURLException);
        }

        return new CreateMissingProjectsAndStreams(initializedLogger.get(), configurationServiceWrapper, projectName, streamName);
    }

    // TODO: Remove Jenkins extension object?
    public GetCoverityCommands createStepGetCoverityCommands(final CoverityRunConfiguration coverityRunConfiguration) {
        return new GetCoverityCommands(initializedLogger.get(), initializedIntEnvrionmentVariables.get(), coverityRunConfiguration);
    }

    public GetIssuesInView createStepGetIssuesInView(final String coverityServerUrl, final String projectName, final String viewName) throws CoverityJenkinsAbortException {
        final WebServiceFactory webServiceFactory = getWebServiceFactoryFromUrl(coverityServerUrl);
        final ConfigurationServiceWrapper configurationServiceWrapper;
        try {
            configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
        } catch (final MalformedURLException malformedURLException) {
            throw CoverityJenkinsAbortException.fromMalformedUrlException(coverityServerUrl + WebServiceFactory.CONFIGURATION_SERVICE_V9_WSDL, malformedURLException);
        }
        final ViewService viewService = webServiceFactory.createViewService();

        return new GetIssuesInView(initializedLogger.get(), configurationServiceWrapper, viewService, projectName, viewName);
    }

    // TODO: Remove Jenkins extension object?
    public ProcessChangeLogSets createStepProcessChangeLogSets(final List<ChangeLogSet<?>> changeLogSets, final ConfigureChangeSetPatterns configureChangeSetPatterns) {
        return new ProcessChangeLogSets(initializedLogger.get(), changeLogSets, configureChangeSetPatterns);
    }

    // TODO: Remove Jenkins extension object?
    public RunCoverityCommands createStepRunCoverityCommands(final String workspaceRemotePath, final OnCommandFailure onCommandFailure) throws CoverityJenkinsAbortException {
        return new RunCoverityCommands(initializedLogger.get(), initializedIntEnvrionmentVariables.get(), workspaceRemotePath, onCommandFailure, initializedVirtualChannel.get());
    }

    public SubStep<Object, Object> createStepCopyAuthenticationKeyFile(final String workspaceRemotePath, final String coverityServerUrl) throws CoverityJenkinsAbortException {
        final CoverityJenkinsIntLogger logger = getOrCreateLogger();
        final CoverityConnectInstance coverityConnectInstance = getCoverityConnectInstanceFromUrl(coverityServerUrl);
        Optional<String> authKeyContents = coverityConnectInstance.getAuthenticationKeyFileContents(logger);
        FilePath filePath = new FilePath(initializedVirtualChannel.get(), workspaceRemotePath).child("auth-key.txt");

        // TODO: Verify that this does what we need
        return SubStep.ofExecutor(() -> {
            if (authKeyContents.isPresent()) {
                filePath.createTextTempFile("auth-key", ".txt", authKeyContents.get());
            }
        });
    }

    public SetUpCoverityEnvironment createStepSetUpCoverityEnvironment(final String workspaceRemotePath, final String coverityServerUrl, final String projectName, final String streamName, final String viewName)
        throws CoverityJenkinsAbortException {
        final CoverityJenkinsIntLogger logger = getOrCreateLogger();
        final FilePath intermediateDirectory = getIntermediateDirectory(workspaceRemotePath);
        final String remoteIntermediateDirectory = intermediateDirectory.getRemote();

        final CoverityConnectInstance coverityConnectInstance = getCoverityConnectInstanceFromUrl(coverityServerUrl);
        String coverityUsername = coverityConnectInstance.getUsername(logger).orElse(StringUtils.EMPTY);
        String coverityPassphrase = coverityConnectInstance.getPassphrase().orElse(StringUtils.EMPTY);

        return new SetUpCoverityEnvironment(initializedLogger.get(), initializedIntEnvrionmentVariables.get(), coverityServerUrl, coverityUsername, coverityPassphrase, projectName, streamName, viewName, remoteIntermediateDirectory);
    }

    public RemoteSubStep<Boolean> createStepValidateCoverityInstallation(final boolean shouldValidateVersion) throws CoverityJenkinsAbortException {
        final String coverityToolHome = initializedIntEnvrionmentVariables.get().getValue(COVERITY_TOOL_HOME.toString());

        final ValidateCoverityInstallation validateCoverityInstallation = new ValidateCoverityInstallation(initializedLogger.get(), shouldValidateVersion, coverityToolHome);
        return RemoteSubStep.of(initializedVirtualChannel.get(), validateCoverityInstallation);
    }

    public SubStep<Object, Object> createStepCleanUpIntermediateDirectory(final String workspaceRemotePath) throws CoverityJenkinsAbortException {
        final FilePath intermediateDirectory = getIntermediateDirectory(workspaceRemotePath);
        return SubStep.ofExecutor(intermediateDirectory::deleteRecursive);
    }

    public SubStep<Object, Object> createStepPopulateEnvVars(final BiConsumer<String, String> environmentPopulator) {
        final IntEnvironmentVariables intEnvironmentVariables = initializedIntEnvrionmentVariables.get();
        return SubStep.ofExecutor(() -> intEnvironmentVariables.getVariables().forEach(environmentPopulator));
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

    public CoverityConnectInstance getCoverityConnectInstanceFromUrl(final String coverityServerUrl) throws CoverityJenkinsAbortException {
        final CoverityGlobalConfig coverityGlobalConfig = GlobalConfiguration.all().get(CoverityGlobalConfig.class);
        if (coverityGlobalConfig == null) {
            throw new CoverityJenkinsAbortException("No Coverity global configuration detected in the Jenkins system configuration.");
        }
        final List<CoverityConnectInstance> coverityConnectInstances = coverityGlobalConfig.getCoverityConnectInstances();
        if (coverityConnectInstances.isEmpty()) {
            throw new CoverityJenkinsAbortException("No Coverity connect instances are configured in the Jenkins system configuration.");
        }

        return coverityConnectInstances.stream()
                   .filter(instance -> instance.getUrl().equals(coverityServerUrl))
                   .findFirst()
                   .orElseThrow(
                       () -> new CoverityJenkinsAbortException("No Coverity conect instance with the url '" + coverityServerUrl + "' could be  found in the Jenkins system configuration."));

    }

    public WebServiceFactory getWebServiceFactoryFromUrl(final String coverityServerUrl) throws CoverityJenkinsAbortException {
        final CoverityConnectInstance coverityConnectInstance = getCoverityConnectInstanceFromUrl(coverityServerUrl);
        final JenkinsIntLogger logger = getOrCreateLogger();

        final CoverityServerConfig coverityServerConfig = coverityConnectInstance.getCoverityServerConfig(logger);
        final WebServiceFactory webServiceFactory = coverityServerConfig.createWebServiceFactory(logger);
        try {
            webServiceFactory.connect();
        } catch (final CoverityIntegrationException e) {
            throw new CoverityJenkinsAbortException("An error occurred when connecting to Coverity Connect. Please ensure that you can connect properly.");
        } catch (final MalformedURLException e) {
            throw CoverityJenkinsAbortException.fromMalformedUrlException(coverityServerUrl + WebServiceFactory.CONFIGURATION_SERVICE_V9_WSDL, e);
        }

        return webServiceFactory;
    }

    public FilePath getIntermediateDirectory(final String workspaceRemotePath) throws CoverityJenkinsAbortException {
        return new FilePath(initializedVirtualChannel.get(), workspaceRemotePath).child("idir");
    }

    public VirtualChannel getOrCreateVirtualChannel() throws CoverityJenkinsAbortException {
        if (_virtualChannel == null) {
            if (launcher != null || node != null) {
                _virtualChannel = Optional.ofNullable(launcher)
                                      .map(Launcher::getChannel)
                                      .orElseGet(node::getChannel);
            }
            if (_virtualChannel == null) {
                throw new CoverityJenkinsAbortException("Node was either not connected or offline.");
            }
        }

        return _virtualChannel;
    }

}
