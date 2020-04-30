package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.CHANGE_SET;
import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType.COV_RUN_DESKTOP;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType.THRESHOLD;
import static com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityRunConfiguration.RunConfigurationType.ADVANCED;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsStepWorkflow;
import com.synopsys.integration.jenkins.coverity.extensions.BuildStatus;
import com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.CleanUpAction;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.stepworkflow.StepWorkflow;
import com.synopsys.integration.stepworkflow.StepWorkflowResponse;
import com.synopsys.integration.stepworkflow.SubStep;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.AbortException;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;

public class CoverityBuildStepWorkflow extends CoverityJenkinsStepWorkflow<Object> {
    public static final String FAILURE_MESSAGE = "Unable to perform Synopsys Coverity static analysis: ";

    private final CoverityWorkflowStepFactory coverityWorkflowStepFactory;
    private final AbstractBuild<?, ?> build;
    private final String projectName;
    private final String streamName;
    private final CoverityRunConfiguration coverityRunConfiguration;
    @Nullable
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;
    @Nullable
    private final CheckForIssuesInView checkForIssuesInView;
    private final OnCommandFailure onCommandFailure;
    private final CleanUpAction cleanUpAction;

    public CoverityBuildStepWorkflow(final JenkinsIntLogger jenkinsIntLogger, final WebServiceFactory webServiceFactory, final CoverityWorkflowStepFactory coverityWorkflowStepFactory, final AbstractBuild<?, ?> build,
        final String projectName, final String streamName, final CoverityRunConfiguration coverityRunConfiguration, final ConfigureChangeSetPatterns configureChangeSetPatterns,
        final CheckForIssuesInView checkForIssuesInView, final OnCommandFailure onCommandFailure, final CleanUpAction cleanUpAction) {
        super(jenkinsIntLogger, webServiceFactory);
        this.coverityWorkflowStepFactory = coverityWorkflowStepFactory;
        this.build = build;
        this.projectName = projectName;
        this.streamName = streamName;
        this.coverityRunConfiguration = coverityRunConfiguration;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
        this.checkForIssuesInView = checkForIssuesInView;
        this.onCommandFailure = onCommandFailure;
        this.cleanUpAction = cleanUpAction;
    }

    @Override
    protected void validate() throws AbortException {
        if (Result.ABORTED.equals(build.getResult())) {
            throw new AbortException(FAILURE_MESSAGE + "The build was aborted.");
        }

        final Node node = build.getBuiltOn();
        if (node == null) {
            throw new AbortException(FAILURE_MESSAGE + "No node was configured or accessible.");
        }

        final VirtualChannel virtualChannel = node.getChannel();
        if (virtualChannel == null) {
            throw new AbortException(FAILURE_MESSAGE + "Configured node \"" + node.getDisplayName() + "\" is either not connected or offline.");
        }
    }

    @Override
    protected StepWorkflow<Object> buildWorkflow() throws AbortException {
        final String viewName = Optional.ofNullable(checkForIssuesInView).map(CheckForIssuesInView::getViewName).orElse(StringUtils.EMPTY);
        final BuildStatus buildStatus = Optional.ofNullable(checkForIssuesInView).map(CheckForIssuesInView::getBuildStatusForIssues).orElse(BuildStatus.SUCCESS);

        return StepWorkflow.first(coverityWorkflowStepFactory.createStepValidateCoverityInstallation(coverityRunConfiguration.getRunConFigurationType()))
                   .then(coverityWorkflowStepFactory.createStepProcessChangeLogSets(build.getChangeSets(), configureChangeSetPatterns))
                   .then(coverityWorkflowStepFactory.createStepSetUpCoverityEnvironment(projectName, streamName, viewName))
                   .then(coverityWorkflowStepFactory.createStepCreateMissingProjectsAndStreams(projectName, streamName))
                   .andSometimes(coverityWorkflowStepFactory.createStepGetCoverityCommands(coverityRunConfiguration))
                        .then(coverityWorkflowStepFactory.createStepRunCoverityCommands(onCommandFailure))
                        .butOnlyIf(coverityWorkflowStepFactory.getOrCreateEnvironmentVariables(), intEnvironmentVariables -> this.shouldRunCoverityCommands(intEnvironmentVariables, coverityRunConfiguration))
                   .andSometimes(coverityWorkflowStepFactory.createStepGetIssuesInView(projectName, viewName))
                        .then(SubStep.ofConsumer(issueCount -> failOnIssuesPresent(issueCount, build, projectName, viewName, buildStatus)))
                        .butOnlyIf(checkForIssuesInView, Objects::nonNull)
                   .andSometimes(coverityWorkflowStepFactory.createStepCleanUpIntermediateDirectory())
                        .butOnlyIf(cleanUpAction, CleanUpAction.DELETE_INTERMEDIATE_DIRECTORY::equals)
                   .build();
    }

    private boolean shouldRunCoverityCommands(final IntEnvironmentVariables intEnvironmentVariables, final CoverityRunConfiguration coverityRunConfiguration) {
        final boolean analysisIsIncremental;
        if (ADVANCED.equals(coverityRunConfiguration.getRunConFigurationType())) {
            analysisIsIncremental = false;
        } else {
            final SimpleCoverityRunConfiguration simpleCoverityRunConfiguration = (SimpleCoverityRunConfiguration) coverityRunConfiguration;
            final int changeSetSize;
            changeSetSize = Integer.parseInt(intEnvironmentVariables.getValue(CHANGE_SET_SIZE.toString(), "0"));
            final CoverityAnalysisType coverityAnalysisType = simpleCoverityRunConfiguration.getCoverityAnalysisType();
            final int changeSetThreshold = simpleCoverityRunConfiguration.getChangeSetAnalysisThreshold();

            analysisIsIncremental = COV_RUN_DESKTOP.equals(coverityAnalysisType) || (THRESHOLD.equals(coverityAnalysisType) && changeSetSize < changeSetThreshold);
        }

        final String changeSetString = intEnvironmentVariables.getValue(CHANGE_SET.toString());
        if (analysisIsIncremental && StringUtils.isBlank(changeSetString)) {
            logger.alwaysLog("Skipping Synopsys Coverity static analysis because the analysis type was determined to be Incremental Analysis and the Jenkins $CHANGE_SET was empty.");
            return false;
        }
        return true;
    }

    private void failOnIssuesPresent(final Integer defectCount, final AbstractBuild<?, ?> build, final String projectName, final String viewName, final BuildStatus buildStatusOnIssues) {
        logger.alwaysLog("Checking for issues in view");
        logger.alwaysLog("-- Build state for issues in the view: " + buildStatusOnIssues.getDisplayName());
        logger.alwaysLog("-- Coverity project name: " + projectName);
        logger.alwaysLog("-- Coverity view name: " + viewName);

        if (defectCount > 0) {
            logger.alwaysLog(String.format("[Coverity] Found %s issues in view.", defectCount));
            logger.alwaysLog("Setting build status to " + buildStatusOnIssues.getResult().toString());
            build.setResult(buildStatusOnIssues.getResult());
        }
    }

    public boolean afterPerform(final StepWorkflowResponse<Object> stepWorkflowResponse, final AbstractBuild<?, ?> build) {
        final boolean wasSuccessful = stepWorkflowResponse.wasSuccessful();
        try {
            if (!wasSuccessful) {
                throw stepWorkflowResponse.getException();
            }
        } catch (final InterruptedException e) {
            logger.error("[ERROR] Synopsys Coverity thread was interrupted.", e);
            build.setResult(Result.ABORTED);
            Thread.currentThread().interrupt();
        } catch (final IntegrationException e) {
            this.handleException(build, Result.FAILURE, e);
        } catch (final Exception e) {
            this.handleException(build, Result.UNSTABLE, e);
        }

        return stepWorkflowResponse.wasSuccessful();
    }

    private void handleException(final AbstractBuild<?, ?> build, final Result result, final Exception e) {
        logger.error("[ERROR] " + e.getMessage());
        logger.debug(e.getMessage(), e);
        build.setResult(result);
    }

}
