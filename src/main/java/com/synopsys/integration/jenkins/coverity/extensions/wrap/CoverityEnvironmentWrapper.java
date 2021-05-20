/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.wrap;

import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.TEMPORARY_AUTH_KEY_PATH;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.jenkins.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.PasswordMaskingOutputStream;
import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectUrlFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.IssueViewFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ProjectStreamFieldHelper;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CleanUpWorkflowService;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.log.SilentIntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;

public class CoverityEnvironmentWrapper extends SimpleBuildWrapper {
    @HelpMarkdown("Specify which Synopsys Coverity connect instance to run this job against.")
    private final String coverityInstanceUrl;
    private final String coverityPassphrase;

    // Any field set by a DataBoundSetter should be explicitly declared as nullable to avoid NPEs
    @Nullable
    @HelpMarkdown("Specify the name of the Coverity project.  \r\n"
                      + "The resulting project name is stored in the $COV_PROJECT environment variable, and will affect both the full and incremental analysis.")
    private String projectName;

    @Nullable
    @HelpMarkdown("Specify the name of the Coverity stream that you would like to use for the commands.  \r\n"
                      + "The resulting stream name is stored in the $COV_STREAM environment variable, and will affect both the full and incremental analysis.")
    private String streamName;

    @Nullable
    @HelpMarkdown("Specify the name of the Coverity view that you would like to check for issues.  \r\n"
                      + "The resulting view name is stored in the $COV_VIEW environment variable, and affects checking for issues in both the full and incremental analysis, if configured.")
    private String viewName;

    @Nullable
    private ConfigureChangeSetPatterns configureChangeSetPatterns;

    @Nullable
    private Boolean createMissingProjectsAndStreams;

    @Nullable
    private String credentialsId;

    @DataBoundConstructor
    public CoverityEnvironmentWrapper(String coverityInstanceUrl) {
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.coverityPassphrase = GlobalValueHelper.getCoverityInstanceWithUrl(new SilentIntLogger(), coverityInstanceUrl)
                                      .flatMap(coverityConnectInstance -> coverityConnectInstance.getPassphrase(credentialsId))
                                      .orElse(StringUtils.EMPTY);
    }

    public Boolean getCreateMissingProjectsAndStreams() {
        if (Boolean.FALSE.equals(createMissingProjectsAndStreams)) {
            return null;
        }
        return createMissingProjectsAndStreams;
    }

    @DataBoundSetter
    public void setCreateMissingProjectsAndStreams(@QueryParameter("createMissingProjectsAndStreams") Boolean createMissingProjectsAndStreams) {
        this.createMissingProjectsAndStreams = createMissingProjectsAndStreams;
    }

    public String getCoverityInstanceUrl() {
        return coverityInstanceUrl;
    }

    public String getProjectName() {
        return projectName;
    }

    @DataBoundSetter
    public void setProjectName(@QueryParameter("projectName") String projectName) {
        this.projectName = projectName;
    }

    public String getStreamName() {
        return streamName;
    }

    @DataBoundSetter
    public void setStreamName(@QueryParameter("streamName") String streamName) {
        this.streamName = streamName;
    }

    public String getViewName() {
        return viewName;
    }

    @DataBoundSetter
    public void setViewName(@QueryParameter("viewName") String viewName) {
        this.viewName = viewName;
    }

    public ConfigureChangeSetPatterns getConfigureChangeSetPatterns() {
        return configureChangeSetPatterns;
    }

    @DataBoundSetter
    public void setConfigureChangeSetPatterns(@QueryParameter("configureChangeSetPatterns") ConfigureChangeSetPatterns configureChangeSetPatterns) {
        this.configureChangeSetPatterns = configureChangeSetPatterns;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        Node node = Optional.ofNullable(workspace.toComputer())
                        .map(Computer::getNode)
                        .orElse(null);
        RunWrapper runWrapper = new RunWrapper(build, true);

        CoverityWorkflowStepFactory coverityWorkflowStepFactory = new CoverityWorkflowStepFactory(initialEnvironment, node, launcher, listener);
        CoverityJenkinsIntLogger logger = coverityWorkflowStepFactory.getOrCreateLogger();
        JenkinsVersionHelper jenkinsVersionHelper = new JenkinsVersionHelper(Jenkins.getInstanceOrNull());
        List<ChangeLogSet<?>> changeLogSets;
        try {
            changeLogSets = runWrapper.getChangeSets();
        } catch (Exception e) {
            logger.warn(String.format("WARNING: Synopsys Coverity for Jenkins could not determine the change set, %s will be empty and %s will be 0.",
                JenkinsCoverityEnvironmentVariable.CHANGE_SET,
                JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE));

            changeLogSets = Collections.emptyList();
        }

        CoverityEnvironmentWrapperStepWorkflow coverityEnvironmentWrapperStepWorkflow = new CoverityEnvironmentWrapperStepWorkflow(
            logger,
            jenkinsVersionHelper,
            () -> coverityWorkflowStepFactory.getWebServiceFactoryFromUrl(credentialsId, coverityInstanceUrl),
            coverityWorkflowStepFactory,
            context,
            workspace.getRemote(),
            coverityInstanceUrl,
            credentialsId,
            projectName,
            streamName,
            viewName,
            createMissingProjectsAndStreams,
            changeLogSets,
            configureChangeSetPatterns
        );
        Boolean environmentInjectedSuccessfully = coverityEnvironmentWrapperStepWorkflow.perform();
        if (Boolean.TRUE.equals(environmentInjectedSuccessfully)) {
            logger.info("Coverity environment injected successfully.");
        }

        context.setDisposer(new DisposerImpl((HashMap<String, String>) coverityWorkflowStepFactory.getOrCreateEnvironmentVariables().getVariables()));
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public ConsoleLogFilter createLoggerDecorator(@Nonnull Run<?, ?> build) {
        return new FilterImpl(coverityPassphrase);
    }

    @Symbol("withCoverityEnvironment")
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        private final CoverityConnectUrlFieldHelper coverityConnectUrlFieldHelper;
        private final ProjectStreamFieldHelper projectStreamFieldHelper;
        private final IssueViewFieldHelper issueViewFieldHelper;

        public DescriptorImpl() {
            super(CoverityEnvironmentWrapper.class);
            load();

            Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
            coverityConnectUrlFieldHelper = new CoverityConnectUrlFieldHelper(slf4jIntLogger);
            projectStreamFieldHelper = new ProjectStreamFieldHelper(slf4jIntLogger);
            issueViewFieldHelper = new IssueViewFieldHelper(slf4jIntLogger);
        }

        public ListBoxModel doFillCoverityInstanceUrlItems() {
            return coverityConnectUrlFieldHelper.doFillCoverityInstanceUrlItems();
        }

        public FormValidation doCheckCoverityInstanceUrl( @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("credentialsId") String credentialsId) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrl(coverityInstanceUrl, credentialsId);
        }

        public ComboBoxModel doFillProjectNameItems(@QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("credentialsId") String credentialsId, @QueryParameter("updateNow") boolean updateNow) throws InterruptedException {
            if (updateNow) {
                projectStreamFieldHelper.updateNow(credentialsId, coverityInstanceUrl);
            }
            return projectStreamFieldHelper.getProjectNamesForComboBox(credentialsId, coverityInstanceUrl);
        }

        public FormValidation doCheckProjectName(@QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("credentialsId") String credentialsId) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl, credentialsId);
        }

        public ComboBoxModel doFillStreamNameItems(@QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("credentialsId") String credentialsId, @QueryParameter("projectName") String projectName) throws InterruptedException {
            return projectStreamFieldHelper.getStreamNamesForComboBox(credentialsId, coverityInstanceUrl, projectName);
        }

        public FormValidation doCheckStreamName(@QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("credentialsId") String credentialsId) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl, credentialsId);
        }

        public ListBoxModel doFillViewNameItems(@QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("credentialsId") String credentialsId, @QueryParameter("updateNow") boolean updateNow) throws InterruptedException {
            if (updateNow) {
                issueViewFieldHelper.updateNow(credentialsId, coverityInstanceUrl);
            }
            return issueViewFieldHelper.getViewNamesForListBox(credentialsId, coverityInstanceUrl);
        }

        public FormValidation doCheckViewName(@QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("credentialsId") String credentialsId) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl, credentialsId);
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Inject Coverity environment into the build process";
        }
    }

    private static final class FilterImpl extends ConsoleLogFilter implements Serializable {
        private static final long serialVersionUID = 1787519634824445328L;
        private final String passwordToMask;

        public FilterImpl(String passwordToMask) {
            this.passwordToMask = passwordToMask;
        }

        @Override
        public OutputStream decorateLogger(Run ignored, OutputStream logger) {
            return new PasswordMaskingOutputStream(logger, passwordToMask);
        }
    }

    private static final class DisposerImpl extends SimpleBuildWrapper.Disposer {
        private final HashMap<String, String> environmentVariables;

        public DisposerImpl(HashMap<String, String> intEnvironmentVariables) {
            this.environmentVariables = intEnvironmentVariables;
        }

        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            IntEnvironmentVariables intEnvironmentVariables = new IntEnvironmentVariables(false);
            intEnvironmentVariables.putAll(environmentVariables);
            CoverityJenkinsIntLogger logger = CoverityJenkinsIntLogger.initializeLogger(listener, intEnvironmentVariables);
            CleanUpWorkflowService cleanUpWorkflowService = new CleanUpWorkflowService(logger);

            String authKeyPath = intEnvironmentVariables.getValue(TEMPORARY_AUTH_KEY_PATH.toString());
            if (StringUtils.isNotBlank(authKeyPath)) {
                FilePath authKeyFile = new FilePath(launcher.getChannel(), authKeyPath);
                cleanUpWorkflowService.cleanUpAuthenticationFile(authKeyFile);
            }
        }
    }

}
