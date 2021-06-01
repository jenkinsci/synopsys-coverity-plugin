package com.synopsys.integration.jenkins.coverity.service;

import java.io.IOException;
import java.util.Optional;

import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.coverity.CoverityFreestyleCommands;
import com.synopsys.integration.jenkins.coverity.CoverityPipelineCommands;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsBuildService;
import com.synopsys.integration.jenkins.service.JenkinsConfigService;
import com.synopsys.integration.jenkins.service.JenkinsRemotingService;
import com.synopsys.integration.jenkins.service.JenkinsServicesFactory;
import com.synopsys.integration.jenkins.wrapper.JenkinsWrapper;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.WorkspaceList;

public class CoverityCommandsFactory {
    private final JenkinsWrapper jenkinsWrapper;
    private final TaskListener listener;
    private final EnvVars envVars;
    private final ThrowingSupplier<FilePath, AbortException> validatedWorkspace;

    private CoverityCommandsFactory(JenkinsWrapper jenkinsWrapper, TaskListener listener, EnvVars envVars, FilePath workspace) {
        this.jenkinsWrapper = jenkinsWrapper;
        this.listener = listener;
        this.envVars = envVars;
        this.validatedWorkspace = () -> Optional.ofNullable(workspace).orElseThrow(() -> new AbortException("Detect cannot be executed when the workspace is null"));
    }

    public static CoverityFreestyleCommands fromPostBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        CoverityCommandsFactory detectCommandsFactory = new CoverityCommandsFactory(JenkinsWrapper.initializeFromJenkinsJVM(), listener, build.getEnvironment(listener), build.getWorkspace());

        JenkinsServicesFactory jenkinsServicesFactory = new JenkinsServicesFactory(detectCommandsFactory.getLogger(), build, build.getEnvironment(listener), launcher, listener, build.getBuiltOn(), build.getWorkspace());
        JenkinsBuildService jenkinsBuildService = jenkinsServicesFactory.createJenkinsBuildService();
        JenkinsConfigService jenkinsConfigService = jenkinsServicesFactory.createJenkinsConfigService();
        JenkinsRemotingService jenkinsRemotingService = jenkinsServicesFactory.createJenkinsRemotingService();

        return new CoverityFreestyleCommands(jenkinsBuildService, detectCommandsFactory.createDetectRunner(jenkinsConfigService, jenkinsRemotingService));
    }

    public static CoverityPipelineCommands fromPipeline(TaskListener listener, EnvVars envVars, Launcher launcher, Node node, FilePath workspace) throws AbortException {
        CoverityCommandsFactory detectCommandsFactory = new CoverityCommandsFactory(JenkinsWrapper.initializeFromJenkinsJVM(), listener, envVars, workspace);

        JenkinsServicesFactory jenkinsServicesFactory = new JenkinsServicesFactory(detectCommandsFactory.getLogger(), null, envVars, launcher, listener, node, workspace);
        JenkinsConfigService jenkinsConfigService = jenkinsServicesFactory.createJenkinsConfigService();
        JenkinsRemotingService jenkinsRemotingService = jenkinsServicesFactory.createJenkinsRemotingService();

        return new CoverityPipelineCommands(detectCommandsFactory.createDetectRunner(jenkinsConfigService, jenkinsRemotingService), detectCommandsFactory.getLogger());
    }

    private DetectRunner createDetectRunner(JenkinsConfigService jenkinsConfigService, JenkinsRemotingService jenkinsRemotingService) throws AbortException {
        return new DetectRunner(createCoverityEnvironmentService(jenkinsConfigService), jenkinsRemotingService, createDetectStrategyService(jenkinsConfigService), createDetectArgumentService());
    }

    private DetectArgumentService createDetectArgumentService() {
        return new DetectArgumentService(getLogger(), jenkinsWrapper.getVersionHelper());
    }

    private CoverityEnvironmentService createCoverityEnvironmentService(JenkinsConfigService jenkinsConfigService) {
        return new CoverityEnvironmentService(getLogger(), createCoverityConfigService(jenkinsConfigService), envVars);
    }

    private ProjectStreamCreationService createProjectStreamCreationService(JenkinsConfigService jenkinsConfigService) {
        return new ProjectStreamCreationService(getLogger(), createCoverityConfigService(jenkinsConfigService));
    }

    private DetectStrategyService createDetectStrategyService(JenkinsConfigService jenkinsConfigService) throws AbortException {
        FilePath workspace = validatedWorkspace.get();
        FilePath workspaceTempDir = WorkspaceList.tempDir(workspace);

        return new DetectStrategyService(getLogger(), jenkinsWrapper.getProxyHelper(), workspaceTempDir.getRemote(), jenkinsConfigService);
    }

    private CoverityConfigService createCoverityConfigService(JenkinsConfigService jenkinsConfigService) {
        return new CoverityConfigService(getLogger(), jenkinsConfigService);
    }

    private JenkinsIntLogger getLogger() {
        return new JenkinsIntLogger(listener);
    }
}
