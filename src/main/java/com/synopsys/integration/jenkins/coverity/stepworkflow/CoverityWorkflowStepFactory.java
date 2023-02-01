/*
 * synopsys-coverity
 *
 * Copyright (c) 2023 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

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
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.wrapper.JenkinsWrapper;
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
    private final ThrowingSupplier<String, CoverityJenkinsAbortException> validatedCoverityToolHome = this::getCoverityToolHomeFromEnvironment;
    private CoverityJenkinsIntLogger _logger = null;
    private final Supplier<CoverityJenkinsIntLogger> initializedLogger = this::getOrCreateLogger;
    private VirtualChannel _virtualChannel = null;
    private final ThrowingSupplier<VirtualChannel, CoverityJenkinsAbortException> initializedVirtualChannel = this::getOrCreateVirtualChannel;

    public CoverityWorkflowStepFactory(EnvVars envVars, Node node, Launcher launcher, TaskListener listener) {
        this.envVars = envVars;
        this.node = node;
        this.launcher = launcher;
        this.listener = listener;
    }

    public CreateMissingProjectsAndStreams createStepCreateMissingProjectsAndStreams(String coverityServerUrl, String credentialsId, String projectName, String streamName) throws CoverityJenkinsAbortException {
        WebServiceFactory webServiceFactory = getWebServiceFactoryFromUrl(coverityServerUrl, credentialsId);
        ConfigurationServiceWrapper configurationServiceWrapper;
        try {
            configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
        } catch (MalformedURLException malformedURLException) {
            throw CoverityJenkinsAbortException.fromMalformedUrlException(coverityServerUrl + WebServiceFactory.CONFIGURATION_SERVICE_V9_WSDL, malformedURLException);
        }

        return new CreateMissingProjectsAndStreams(initializedLogger.get(), configurationServiceWrapper, projectName, streamName);
    }

    public GetCoverityCommands createStepGetCoverityCommands(CoverityRunConfiguration coverityRunConfiguration) {
        return new GetCoverityCommands(initializedLogger.get(), initializedIntEnvrionmentVariables.get(), coverityRunConfiguration);
    }

    public GetIssuesInView createStepGetIssuesInView(String coverityServerUrl, String credentialsId, String projectName, String viewName) throws CoverityJenkinsAbortException {
        WebServiceFactory webServiceFactory = getWebServiceFactoryFromUrl(coverityServerUrl, credentialsId);
        ConfigurationServiceWrapper configurationServiceWrapper;
        try {
            configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
        } catch (MalformedURLException malformedURLException) {
            throw CoverityJenkinsAbortException.fromMalformedUrlException(coverityServerUrl + WebServiceFactory.CONFIGURATION_SERVICE_V9_WSDL, malformedURLException);
        }
        ViewService viewService = webServiceFactory.createViewService();

        return new GetIssuesInView(initializedLogger.get(), configurationServiceWrapper, viewService, projectName, viewName);
    }

    public RunCoverityCommands createStepRunCoverityCommands(String workspaceRemotePath, OnCommandFailure onCommandFailure) throws CoverityJenkinsAbortException {
        return new RunCoverityCommands(initializedLogger.get(), initializedIntEnvrionmentVariables.get(), workspaceRemotePath, onCommandFailure, initializedVirtualChannel.get());
    }

    public SubStep<Object, String> createStepCreateAuthenticationKeyFile(String workspaceRemotePath, String coverityServerUrl, String credentialsId) throws CoverityJenkinsAbortException {
        CoverityJenkinsIntLogger logger = initializedLogger.get();
        CoverityConnectInstance coverityConnectInstance = getCoverityConnectInstanceFromUrl(coverityServerUrl);
        Optional<String> authKeyContents = coverityConnectInstance.getAuthenticationKeyFileContents(logger, credentialsId);
        FilePath workspace = new FilePath(initializedVirtualChannel.get(), workspaceRemotePath);

        return SubStep.ofSupplier(() -> {
            if (authKeyContents.isPresent()) {
                FilePath authKeyFile = workspace.createTextTempFile("auth-key", ".txt", authKeyContents.get());
                authKeyFile.chmod(0600);
                return authKeyFile.getRemote();
            }
            return StringUtils.EMPTY;
        });
    }

    public SetUpCoverityEnvironment createStepSetUpCoverityEnvironment(List<ChangeLogSet<?>> changeLogSets, ConfigureChangeSetPatterns configureChangeSetPatterns, String workspaceRemotePath, String coverityServerUrl, String credentialsId, String projectName,
        String streamName, String viewName) throws CoverityJenkinsAbortException {
        CoverityJenkinsIntLogger logger = initializedLogger.get();
        IntEnvironmentVariables intEnvironmentVariables = initializedIntEnvrionmentVariables.get();
        VirtualChannel virtualChannel = initializedVirtualChannel.get();

        FilePath intermediateDirectory = getIntermediateDirectory(workspaceRemotePath);
        String remoteIntermediateDirectory = intermediateDirectory.getRemote();

        CoverityConnectInstance coverityConnectInstance = getCoverityConnectInstanceFromUrl(coverityServerUrl);
        String coverityUsername = coverityConnectInstance.getUsername(logger, credentialsId).orElse(null);
        String coverityPassphrase = coverityConnectInstance.getPassphrase(credentialsId).orElse(null);
        String coverityToolHomeBin = new FilePath(virtualChannel, validatedCoverityToolHome.get())
                                         .child("bin")
                                         .getRemote();

        return new SetUpCoverityEnvironment(logger, intEnvironmentVariables, changeLogSets, configureChangeSetPatterns, coverityServerUrl, credentialsId, coverityUsername, coverityPassphrase, projectName, streamName, viewName, remoteIntermediateDirectory, coverityToolHomeBin);
    }

    public RemoteSubStep<Boolean> createStepValidateCoverityInstallation(boolean shouldValidateVersion) throws CoverityJenkinsAbortException {
        String coverityToolHome = validatedCoverityToolHome.get();

        ValidateCoverityInstallation validateCoverityInstallation = new ValidateCoverityInstallation(initializedLogger.get(), shouldValidateVersion, coverityToolHome);
        return RemoteSubStep.of(initializedVirtualChannel.get(), validateCoverityInstallation);
    }

    public SubStep<Object, Object> createStepPopulateEnvVars(BiConsumer<String, String> environmentPopulator) {
        IntEnvironmentVariables intEnvironmentVariables = initializedIntEnvrionmentVariables.get();
        return SubStep.ofExecutor(() -> intEnvironmentVariables.getVariables().forEach(environmentPopulator));
    }

    public CoverityJenkinsIntLogger getOrCreateLogger() {
        IntEnvironmentVariables intEnvironmentVariables = getOrCreateEnvironmentVariables();
        if (_logger == null) {
            JenkinsVersionHelper jenkinsVersionHelper = JenkinsWrapper.initializeFromJenkinsJVM().getVersionHelper();
            _logger = CoverityJenkinsIntLogger.initializeLogger(listener, intEnvironmentVariables);
            _logger.logInitializationMessage(jenkinsVersionHelper);
        }
        return _logger;
    }

    public IntEnvironmentVariables getOrCreateEnvironmentVariables() {
        if (_intEnvironmentVariables == null) {
            _intEnvironmentVariables = IntEnvironmentVariables.empty();
            _intEnvironmentVariables.putAll(envVars);
        }
        return _intEnvironmentVariables;
    }

    @Nonnull
    public String getCoverityToolHomeFromEnvironment() throws CoverityJenkinsAbortException {
        IntEnvironmentVariables intEnvironmentVariables = initializedIntEnvrionmentVariables.get();

        String coverityToolHome = intEnvironmentVariables.getValue(COVERITY_TOOL_HOME.toString());
        if (StringUtils.isBlank(coverityToolHome)) {
            throw new CoverityJenkinsAbortException(String.format("Environment variable $%1$s is not set. Please set $%1$s to the path to your Coverity tool installation.", COVERITY_TOOL_HOME.toString()));
        }

        return coverityToolHome;
    }

    public CoverityConnectInstance getCoverityConnectInstanceFromUrl(String coverityServerUrl) throws CoverityJenkinsAbortException {
        CoverityGlobalConfig coverityGlobalConfig = GlobalConfiguration.all().get(CoverityGlobalConfig.class);
        if (coverityGlobalConfig == null) {
            throw new CoverityJenkinsAbortException("No Coverity global configuration detected in the Jenkins system configuration.");
        }
        List<CoverityConnectInstance> coverityConnectInstances = coverityGlobalConfig.getCoverityConnectInstances();
        if (coverityConnectInstances.isEmpty()) {
            throw new CoverityJenkinsAbortException("No Coverity connect instances are configured in the Jenkins system configuration.");
        }

        return coverityConnectInstances.stream()
                   .filter(instance -> instance.getUrl().equals(coverityServerUrl))
                   .findFirst()
                   .orElseThrow(
                       () -> new CoverityJenkinsAbortException("No Coverity conect instance with the url '" + coverityServerUrl + "' could be  found in the Jenkins system configuration."));

    }

    public WebServiceFactory getWebServiceFactoryFromUrl(String coverityServerUrl, String credentialsId) throws CoverityJenkinsAbortException {
        CoverityConnectInstance coverityConnectInstance = getCoverityConnectInstanceFromUrl(coverityServerUrl);
        JenkinsIntLogger logger = getOrCreateLogger();

        CoverityServerConfig coverityServerConfig = coverityConnectInstance.getCoverityServerConfig(logger, credentialsId);
        WebServiceFactory webServiceFactory = coverityServerConfig.createWebServiceFactory(logger);
        try {
            webServiceFactory.connect();
        } catch (CoverityIntegrationException e) {
            throw new CoverityJenkinsAbortException("An error occurred when connecting to Coverity Connect. Please ensure that you can connect properly.");
        } catch (MalformedURLException e) {
            throw CoverityJenkinsAbortException.fromMalformedUrlException(coverityServerUrl + WebServiceFactory.CONFIGURATION_SERVICE_V9_WSDL, e);
        }

        return webServiceFactory;
    }

    public FilePath getIntermediateDirectory(String workspaceRemotePath) throws CoverityJenkinsAbortException {
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
