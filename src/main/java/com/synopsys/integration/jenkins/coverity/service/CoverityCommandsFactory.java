/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.service;

import java.io.IOException;
import java.util.Optional;

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.coverity.CoverityDisposerCommands;
import com.synopsys.integration.jenkins.coverity.CoverityFreestyleCommands;
import com.synopsys.integration.jenkins.coverity.CoverityPipelineCommands;
import com.synopsys.integration.jenkins.coverity.CoverityWrapperCommands;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsBuildService;
import com.synopsys.integration.jenkins.service.JenkinsConfigService;
import com.synopsys.integration.jenkins.service.JenkinsFreestyleServicesFactory;
import com.synopsys.integration.jenkins.service.JenkinsPipelineFlowService;
import com.synopsys.integration.jenkins.service.JenkinsPipelineServicesFactory;
import com.synopsys.integration.jenkins.service.JenkinsRemotingService;
import com.synopsys.integration.jenkins.service.JenkinsRunService;
import com.synopsys.integration.jenkins.service.JenkinsServicesFactory;
import com.synopsys.integration.jenkins.service.JenkinsWrapperContextService;
import com.synopsys.integration.jenkins.wrapper.JenkinsWrapper;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.tasks.SimpleBuildWrapper;

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

        JenkinsFreestyleServicesFactory jenkinsServicesFactory = new JenkinsFreestyleServicesFactory(coverityCommandsFactory.getLogger(), build, build.getEnvironment(listener), launcher, listener, build.getBuiltOn(), build.getWorkspace());
        JenkinsConfigService jenkinsConfigService = jenkinsServicesFactory.createJenkinsConfigService();
        JenkinsRunService jenkinsRunService = jenkinsServicesFactory.createJenkinsRunService();
        JenkinsRemotingService jenkinsRemotingService = jenkinsServicesFactory.createJenkinsRemotingService();
        JenkinsBuildService jenkinsBuildService = jenkinsServicesFactory.createJenkinsBuildService();

        return new CoverityFreestyleCommands(
            coverityCommandsFactory.getLogger(),
            jenkinsBuildService,
            coverityCommandsFactory.createCoverityPhoneHomeService(jenkinsConfigService),
            coverityCommandsFactory.createCoverityWorkspaceService(jenkinsRemotingService, jenkinsConfigService),
            coverityCommandsFactory.createCoverityEnvironmentService(jenkinsConfigService, jenkinsRunService),
            coverityCommandsFactory.createProjectStreamCreationService(jenkinsConfigService),
            coverityCommandsFactory.createCoverityCommandService(jenkinsRemotingService),
            coverityCommandsFactory.createIssuesInViewService(jenkinsConfigService),
            coverityCommandsFactory.createCleanUpWorkflowService(jenkinsRemotingService)
        );
    }

    public static CoverityPipelineCommands fromPipeline(EnvVars envVars, FlowNode flowNode, Launcher launcher, TaskListener listener, Node node, Run<?, ?> run, FilePath workspace) {
        CoverityCommandsFactory coverityCommandsFactory = new CoverityCommandsFactory(JenkinsWrapper.initializeFromJenkinsJVM(), listener, envVars, workspace);

        JenkinsPipelineServicesFactory jenkinsServicesFactory = new JenkinsPipelineServicesFactory(coverityCommandsFactory.getLogger(), envVars, flowNode, launcher, listener, node, run, workspace);
        JenkinsConfigService jenkinsConfigService = jenkinsServicesFactory.createJenkinsConfigService();
        JenkinsRunService jenkinsRunService = jenkinsServicesFactory.createJenkinsRunService();
        JenkinsPipelineFlowService jenkinsPipelineFlowService = jenkinsServicesFactory.createJenkinsPipelineFlowService();

        return new CoverityPipelineCommands(
            coverityCommandsFactory.getLogger(),
            jenkinsPipelineFlowService,
            jenkinsRunService,
            coverityCommandsFactory.createCoverityPhoneHomeService(jenkinsConfigService),
            coverityCommandsFactory.createIssuesInViewService(jenkinsConfigService)
        );
    }

    public static CoverityWrapperCommands fromBuildWrapper(SimpleBuildWrapper.Context context, Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars envVars) throws AbortException {
        Node node = Optional.ofNullable(workspace.toComputer())
                        .map(Computer::getNode)
                        .orElse(null);

        CoverityCommandsFactory coverityCommandsFactory = new CoverityCommandsFactory(JenkinsWrapper.initializeFromJenkinsJVM(), listener, envVars, workspace);

        JenkinsServicesFactory jenkinsServicesFactory = new JenkinsServicesFactory(coverityCommandsFactory.getLogger(), envVars, launcher, listener, node, run, workspace);
        JenkinsConfigService jenkinsConfigService = jenkinsServicesFactory.createJenkinsConfigService();
        JenkinsRunService jenkinsRunService = jenkinsServicesFactory.createJenkinsRunService();
        JenkinsRemotingService jenkinsRemotingService = jenkinsServicesFactory.createJenkinsRemotingService();
        JenkinsWrapperContextService jenkinsWrapperContextService = jenkinsServicesFactory.createJenkinsWrapperContextService(context);

        return new CoverityWrapperCommands(
            coverityCommandsFactory.getLogger(),
            coverityCommandsFactory.createCoverityPhoneHomeService(jenkinsConfigService),
            coverityCommandsFactory.createCoverityWorkspaceService(jenkinsRemotingService, jenkinsConfigService),
            coverityCommandsFactory.createCoverityEnvironmentService(jenkinsConfigService, jenkinsRunService),
            jenkinsWrapperContextService,
            coverityCommandsFactory.createProjectStreamCreationService(jenkinsConfigService)
        );
    }

    public static CoverityDisposerCommands fromDisposer(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars envVars) throws AbortException {
        Node node = Optional.ofNullable(workspace.toComputer())
                        .map(Computer::getNode)
                        .orElse(null);

        CoverityCommandsFactory coverityCommandsFactory = new CoverityCommandsFactory(JenkinsWrapper.initializeFromJenkinsJVM(), listener, envVars, workspace);

        JenkinsServicesFactory jenkinsServicesFactory = new JenkinsServicesFactory(coverityCommandsFactory.getLogger(), envVars, launcher, listener, node, run, workspace);
        JenkinsConfigService jenkinsConfigService = jenkinsServicesFactory.createJenkinsConfigService();
        JenkinsRunService jenkinsRunService = jenkinsServicesFactory.createJenkinsRunService();
        JenkinsRemotingService jenkinsRemotingService = jenkinsServicesFactory.createJenkinsRemotingService();

        return new CoverityDisposerCommands(
            coverityCommandsFactory.createCoverityEnvironmentService(jenkinsConfigService, jenkinsRunService),
            coverityCommandsFactory.createCleanUpWorkflowService(jenkinsRemotingService)
        );
    }

    private CleanUpWorkflowService createCleanUpWorkflowService(JenkinsRemotingService jenkinsRemotingService) {
        return new CleanUpWorkflowService(getLogger(), jenkinsRemotingService);
    }

    private IssuesInViewService createIssuesInViewService(JenkinsConfigService jenkinsConfigService) {
        return new IssuesInViewService(getLogger(), createCoverityConfigService(jenkinsConfigService));
    }

    private CoverityCommandService createCoverityCommandService(JenkinsRemotingService jenkinsRemotingService) {
        return new CoverityCommandService(getLogger(), jenkinsRemotingService);
    }

    private CoverityWorkspaceService createCoverityWorkspaceService(JenkinsRemotingService jenkinsRemotingService, JenkinsConfigService jenkinsConfigService) {
        return new CoverityWorkspaceService(getLogger(), jenkinsRemotingService, createCoverityConfigService(jenkinsConfigService));
    }

    private CoverityPhoneHomeService createCoverityPhoneHomeService(JenkinsConfigService jenkinsConfigService) {
        return new CoverityPhoneHomeService(getLogger(), jenkinsWrapper.getVersionHelper(), createCoverityConfigService(jenkinsConfigService));
    }

    private CoverityEnvironmentService createCoverityEnvironmentService(JenkinsConfigService jenkinsConfigService, JenkinsRunService jenkinsRunService) {
        return new CoverityEnvironmentService(getLogger(), createCoverityConfigService(jenkinsConfigService), envVars, jenkinsRunService);
    }

    private ProjectStreamCreationService createProjectStreamCreationService(JenkinsConfigService jenkinsConfigService) {
        return new ProjectStreamCreationService(getLogger(), createCoverityConfigService(jenkinsConfigService));
    }

    private CoverityConfigService createCoverityConfigService(JenkinsConfigService jenkinsConfigService) {
        return new CoverityConfigService(getLogger(), jenkinsConfigService);
    }

    private JenkinsIntLogger getLogger() {
        return JenkinsIntLogger.logToListener(listener);
    }
}

