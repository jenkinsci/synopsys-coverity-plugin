package com.synopsys.integration.jenkins.coverity.service;

import java.io.IOException;
import java.util.Optional;

import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.coverity.CoverityFreestyleCommands;
import com.synopsys.integration.jenkins.coverity.CoverityPipelineCommands;
import com.synopsys.integration.jenkins.coverity.service.common.CoverityBuildService;
import com.synopsys.integration.jenkins.coverity.service.common.CoverityRemotingService;
import com.synopsys.integration.jenkins.coverity.service.common.CoverityRunService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
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
import hudson.model.Run;
import hudson.model.TaskListener;

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
        CoverityCommandsFactory coverityCommandsFactory = new CoverityCommandsFactory(JenkinsWrapper.initializeFromJenkinsJVM(), listener, build.getEnvironment(listener), build.getWorkspace());

        JenkinsServicesFactory jenkinsServicesFactory = new JenkinsServicesFactory(coverityCommandsFactory.getLogger(), build, build.getEnvironment(listener), launcher, listener, build.getBuiltOn(), build.getWorkspace());
        JenkinsConfigService jenkinsConfigService = jenkinsServicesFactory.createJenkinsConfigService();

        CoverityBuildService coverityBuildService = new CoverityBuildService(coverityCommandsFactory.getLogger(), build);
        CoverityRemotingService coverityRemotingService = new CoverityRemotingService(launcher, build.getWorkspace() ,listener);

        return new CoverityFreestyleCommands(
            coverityCommandsFactory.getLogger(),
            coverityBuildService,
            coverityCommandsFactory.createCoverityPhoneHomeService(jenkinsConfigService),
            coverityCommandsFactory.createCoverityWorkspaceService(coverityRemotingService, jenkinsConfigService),
            coverityCommandsFactory.createCoverityEnvironmentService(jenkinsConfigService, coverityBuildService),
            coverityCommandsFactory.createProjectStreamCreationService(jenkinsConfigService),
            coverityCommandsFactory.createCoverityCommandService(coverityRemotingService),
            coverityCommandsFactory.createIssuesInViewService(jenkinsConfigService),
            coverityCommandsFactory.createCleanUpWorkflowService(coverityRemotingService)
        );
    }

    public static CoverityPipelineCommands fromPipeline(TaskListener listener, EnvVars envVars, Run<?, ?> run, Launcher launcher, Node node, FilePath workspace) {
        CoverityCommandsFactory coverityCommandsFactory = new CoverityCommandsFactory(JenkinsWrapper.initializeFromJenkinsJVM(), listener, envVars, workspace);

        JenkinsServicesFactory jenkinsServicesFactory = new JenkinsServicesFactory(coverityCommandsFactory.getLogger(), null, envVars, launcher, listener, node, workspace);
        JenkinsConfigService jenkinsConfigService = jenkinsServicesFactory.createJenkinsConfigService();

        CoverityRunService coverityRunService = new CoverityRunService(run);

        return new CoverityPipelineCommands(
            coverityCommandsFactory.getLogger(),
            coverityRunService,
            coverityCommandsFactory.createCoverityPhoneHomeService(jenkinsConfigService),
            coverityCommandsFactory.createIssuesInViewService(jenkinsConfigService)
        );
    }

    private CleanUpWorkflowService createCleanUpWorkflowService(CoverityRemotingService coverityRemotingService) {
        return new CleanUpWorkflowService(getLogger(), coverityRemotingService);
    }

    private IssuesInViewService createIssuesInViewService(JenkinsConfigService jenkinsConfigService) {
        return new IssuesInViewService(getLogger(), createCoverityConfigService(jenkinsConfigService));
    }

    private CoverityCommandService createCoverityCommandService(JenkinsRemotingService jenkinsRemotingService) {
        return new CoverityCommandService(getLogger(), jenkinsRemotingService);
    }

    private CoverityWorkspaceService createCoverityWorkspaceService(CoverityRemotingService coverityRemotingService, JenkinsConfigService jenkinsConfigService) {
        return new CoverityWorkspaceService(getLogger(), coverityRemotingService, createCoverityConfigService(jenkinsConfigService));
    }

    private CoverityPhoneHomeService createCoverityPhoneHomeService(JenkinsConfigService jenkinsConfigService) {
        return new CoverityPhoneHomeService(getLogger(), jenkinsWrapper.getVersionHelper(), createCoverityConfigService(jenkinsConfigService));
    }

    private CoverityEnvironmentService createCoverityEnvironmentService(JenkinsConfigService jenkinsConfigService, CoverityBuildService coverityBuildService) {
        return new CoverityEnvironmentService(getLogger(), createCoverityConfigService(jenkinsConfigService), envVars, coverityBuildService);
    }

    private ProjectStreamCreationService createProjectStreamCreationService(JenkinsConfigService jenkinsConfigService) {
        return new ProjectStreamCreationService(getLogger(), createCoverityConfigService(jenkinsConfigService));
    }

    private CoverityConfigService createCoverityConfigService(JenkinsConfigService jenkinsConfigService) {
        return new CoverityConfigService(getLogger(), jenkinsConfigService);
    }

    private JenkinsIntLogger getLogger() {
        return new JenkinsIntLogger(listener);
    }
}
