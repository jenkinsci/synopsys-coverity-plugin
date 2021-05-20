/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import java.io.IOException;
import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.jenkins.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.CleanUpAction;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectUrlFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ProjectStreamFieldHelper;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;
import com.synopsys.integration.log.Slf4jIntLogger;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class CoverityBuildStep extends Builder {

    @HelpMarkdown("Specify which Synopsys Coverity connect instance to run this job against.  \r\n"
                      + "The resulting Synopsys Coverity connect instance URL is stored in the $COV_URL environment variable, and will affect both the full and incremental analysis.")
    private final String coverityInstanceUrl;

    @HelpMarkdown("Specify the name of the Coverity project.  \r\n"
                      + "The resulting project name is stored in the $COV_PROJECT environment variable, and will affect both the full and incremental analysis.")
    private final String projectName;

    @HelpMarkdown("Specify the name of the Coverity stream that you would like to use for the commands.  \r\n"
                      + "The resulting stream name is stored in the $COV_STREAM environment variable, and will affect both the full and incremental analysis.")
    private final String streamName;

    private final CheckForIssuesInView checkForIssuesInView;

    private final ConfigureChangeSetPatterns configureChangeSetPatterns;

    private final CoverityRunConfiguration coverityRunConfiguration;

    @HelpMarkdown("Specify the action to take if a Coverity static analysis command fails.")
    private final OnCommandFailure onCommandFailure;

    // Any field set by a DataBoundSetter should be explicitly declared as @Nullable to avoid accidental NPEs -- rotte 10/21/2019
    @Nullable
    @HelpMarkdown("Specify the clean up action to perform on a successful execution.  \r\n"
                      + "Will either persist or delete the intermediate directory created by the specified capture type.")
    private CleanUpAction cleanUpAction;

    private String credentialsId;

    @DataBoundConstructor
    public CoverityBuildStep(String coverityInstanceUrl, String onCommandFailure, String projectName, String streamName, CheckForIssuesInView checkForIssuesInView,
        ConfigureChangeSetPatterns configureChangeSetPatterns, CoverityRunConfiguration coverityRunConfiguration) {
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.projectName = projectName;
        this.streamName = streamName;
        this.checkForIssuesInView = checkForIssuesInView;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
        this.coverityRunConfiguration = coverityRunConfiguration;
        this.onCommandFailure = OnCommandFailure.valueOf(onCommandFailure);
    }

    public CleanUpAction getCleanUpAction() {
        return cleanUpAction;
    }

    @DataBoundSetter
    public void setCleanUpAction(CleanUpAction cleanUpAction) {
        this.cleanUpAction = cleanUpAction;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getCoverityInstanceUrl() {
        return coverityInstanceUrl;
    }

    public OnCommandFailure getOnCommandFailure() {
        return onCommandFailure;
    }

    public ConfigureChangeSetPatterns getConfigureChangeSetPatterns() {
        return configureChangeSetPatterns;
    }

    public CheckForIssuesInView getCheckForIssuesInView() {
        return checkForIssuesInView;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getStreamName() {
        return streamName;
    }

    public CoverityRunConfiguration getCoverityRunConfiguration() {
        return coverityRunConfiguration;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        String remoteWorkingDirectoryPath = computeRemoteWorkingDirectory(coverityRunConfiguration, build.getWorkspace(), build.getProject());

        CoverityWorkflowStepFactory coverityWorkflowStepFactory = new CoverityWorkflowStepFactory(build.getEnvironment(listener), build.getBuiltOn(), launcher, listener);
        JenkinsIntLogger logger = coverityWorkflowStepFactory.getOrCreateLogger();
        JenkinsVersionHelper jenkinsVersionHelper = new JenkinsVersionHelper(Jenkins.getInstanceOrNull());
        CoverityBuildStepWorkflow coverityBuildStepWorkflow = new CoverityBuildStepWorkflow(
            logger,
            jenkinsVersionHelper,
            () -> coverityWorkflowStepFactory.getWebServiceFactoryFromUrl(credentialsId, coverityInstanceUrl),
            coverityWorkflowStepFactory,
            build,
            remoteWorkingDirectoryPath,
            coverityInstanceUrl,
            credentialsId,
            projectName,
            streamName,
            coverityRunConfiguration,
            configureChangeSetPatterns,
            checkForIssuesInView,
            onCommandFailure,
            cleanUpAction
        );

        return coverityBuildStepWorkflow.perform();
    }

    private String computeRemoteWorkingDirectory(CoverityRunConfiguration coverityRunConfiguration, FilePath buildWorkspace, AbstractProject<?, ?> project) {
        boolean isDefaultCoverityWorkflow = CoverityRunConfiguration.RunConfigurationType.SIMPLE.equals(coverityRunConfiguration.getRunConFigurationType());
        String customWorkingDirectory = isDefaultCoverityWorkflow ? ((SimpleCoverityRunConfiguration) coverityRunConfiguration).getCustomWorkingDirectory() : null;

        if (StringUtils.isNotBlank(customWorkingDirectory)) {
            return customWorkingDirectory;
        } else if (buildWorkspace != null) {
            return buildWorkspace.getRemote();
        } else {
            return project.getCustomWorkspace();
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private final CoverityConnectUrlFieldHelper coverityConnectUrlFieldHelper;
        private final ProjectStreamFieldHelper projectStreamFieldHelper;

        public DescriptorImpl() {
            super(CoverityBuildStep.class);
            load();

            Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
            coverityConnectUrlFieldHelper = new CoverityConnectUrlFieldHelper(slf4jIntLogger);
            projectStreamFieldHelper = new ProjectStreamFieldHelper(slf4jIntLogger);
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Execute Synopsys Coverity static analysis";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public ListBoxModel doFillCoverityInstanceUrlItems() {
            return coverityConnectUrlFieldHelper.doFillCoverityInstanceUrlItems();
        }

        public FormValidation doCheckCoverityInstanceUrl(@QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter String credentialsId) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrl(coverityInstanceUrl, credentialsId);
        }

        public ComboBoxModel doFillProjectNameItems(@QueryParameter("credentialsId") String credentialsId, @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("updateNow") boolean updateNow) throws InterruptedException {
            if (updateNow) {
                projectStreamFieldHelper.updateNow(credentialsId, coverityInstanceUrl);
            }
            return projectStreamFieldHelper.getProjectNamesForComboBox(credentialsId, coverityInstanceUrl);
        }

        public FormValidation doCheckProjectName(@QueryParameter("credentialsId") String credentialsId, @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("projectName") String projectName) {
            return FormValidation.aggregate(Arrays.asList(
                coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl, credentialsId),
                projectStreamFieldHelper.checkForProjectInCache(credentialsId, coverityInstanceUrl, projectName)
            ));
        }

        public ComboBoxModel doFillStreamNameItems(@QueryParameter("credentialsId") String credentialsId, @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("projectName") String projectName) throws InterruptedException {
            return projectStreamFieldHelper.getStreamNamesForComboBox(credentialsId, coverityInstanceUrl, projectName);
        }

        public FormValidation doCheckStreamName(@QueryParameter("credentialsId") String credentialsId, @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("projectName") String projectName, @QueryParameter("streamName") String streamName) {
            return FormValidation.aggregate(Arrays.asList(
                coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl, credentialsId),
                projectStreamFieldHelper.checkForStreamInCache(credentialsId, coverityInstanceUrl, projectName, streamName)
            ));
        }

        public ListBoxModel doFillOnCommandFailureItems() {
            return JenkinsSelectBoxEnum.toListBoxModel(OnCommandFailure.values());
        }

        public ListBoxModel doFillCleanUpActionItems() {
            return JenkinsSelectBoxEnum.toListBoxModel(CleanUpAction.values());
        }

        public CoverityRunConfiguration getDefaultCoverityRunConfiguration() {
            return SimpleCoverityRunConfiguration.DEFAULT_CONFIGURATION();
        }

    }

}
