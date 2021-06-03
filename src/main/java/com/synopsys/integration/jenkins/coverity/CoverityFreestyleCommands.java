package com.synopsys.integration.jenkins.coverity;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.coverity.api.rest.ViewContents;
import com.synopsys.integration.coverity.ws.view.ViewReportWrapper;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.actions.IssueReportAction;
import com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.CleanUpAction;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.service.CleanUpWorkflowService;
import com.synopsys.integration.jenkins.coverity.service.CoverityCommandService;
import com.synopsys.integration.jenkins.coverity.service.CoverityEnvironmentService;
import com.synopsys.integration.jenkins.coverity.service.CoverityPhoneHomeService;
import com.synopsys.integration.jenkins.coverity.service.CoverityWorkspaceService;
import com.synopsys.integration.jenkins.coverity.service.IssuesInViewService;
import com.synopsys.integration.jenkins.coverity.service.ProjectStreamCreationService;
import com.synopsys.integration.jenkins.coverity.service.common.CoverityBuildService;
import com.synopsys.integration.jenkins.extensions.ChangeBuildStatusTo;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.util.IntEnvironmentVariables;

public class CoverityFreestyleCommands {
    private final JenkinsIntLogger logger;
    private final CoverityBuildService coverityBuildService;
    private final CoverityPhoneHomeService coverityPhoneHomeService;
    private final CoverityWorkspaceService coverityWorkspaceService;
    private final CoverityEnvironmentService coverityEnvironmentService;
    private final ProjectStreamCreationService projectStreamCreationService;
    private final CoverityCommandService coverityCommandService;
    private final IssuesInViewService issuesInViewService;
    private final CleanUpWorkflowService cleanUpWorkflowService;

    public CoverityFreestyleCommands(JenkinsIntLogger logger, CoverityBuildService coverityBuildService, CoverityPhoneHomeService coverityPhoneHomeService,
        CoverityWorkspaceService coverityWorkspaceService, CoverityEnvironmentService coverityEnvironmentService, ProjectStreamCreationService projectStreamCreationService,
        CoverityCommandService coverityCommandService, IssuesInViewService issuesInViewService, CleanUpWorkflowService cleanUpWorkflowService){
        this.logger = logger;
        this.coverityBuildService = coverityBuildService;
        this.coverityPhoneHomeService = coverityPhoneHomeService;
        this.coverityWorkspaceService = coverityWorkspaceService;
        this.coverityEnvironmentService = coverityEnvironmentService;
        this.projectStreamCreationService = projectStreamCreationService;
        this.coverityCommandService = coverityCommandService;
        this.issuesInViewService = issuesInViewService;
        this.cleanUpWorkflowService = cleanUpWorkflowService;
    }

    public void runCoverityCommands(String coverityInstanceUrl, String credentialsId, String projectName, String streamName, CoverityRunConfiguration coverityRunConfiguration, ConfigureChangeSetPatterns configureChangeSetPatterns,
        CheckForIssuesInView checkForIssuesInView, OnCommandFailure onCommandFailure, CleanUpAction cleanUpAction) {
        coverityPhoneHomeService.phoneHome(coverityInstanceUrl, credentialsId);
        String viewName = Optional.ofNullable(checkForIssuesInView).map(CheckForIssuesInView::getViewName).orElse(StringUtils.EMPTY);
        ChangeBuildStatusTo buildStatus = Optional.ofNullable(checkForIssuesInView).map(CheckForIssuesInView::getBuildStatusForIssues).orElse(ChangeBuildStatusTo.SUCCESS);

        boolean shouldValidateVersion = CoverityRunConfiguration.RunConfigurationType.SIMPLE.equals(coverityRunConfiguration.getRunConFigurationType());

        try {
            String authKeyFilePath = null;
            String intermediateDirectoryPath = null;
            try {
                String coverityToolHomeBin = coverityWorkspaceService.getValidatedCoverityToolHomeBin(shouldValidateVersion, coverityEnvironmentService.getCoverityToolHome());
                authKeyFilePath = coverityWorkspaceService.createAuthenticationKeyFile(coverityInstanceUrl, credentialsId);
                intermediateDirectoryPath = coverityWorkspaceService.getIntermediateDirectoryPath();

                IntEnvironmentVariables coverityEnvironment = coverityEnvironmentService.createCoverityEnvironment(
                    configureChangeSetPatterns,
                    coverityInstanceUrl,
                    credentialsId,
                    projectName,
                    streamName,
                    viewName,
                    intermediateDirectoryPath,
                    coverityToolHomeBin,
                    authKeyFilePath
                );

                projectStreamCreationService.createMissingProjectOrStream(coverityInstanceUrl, credentialsId, projectName, streamName);
                if (coverityCommandService.shouldRunCoverityCommands(coverityEnvironment, coverityRunConfiguration)) {
                    List<List<String>> coverityCommands = coverityCommandService.getCommands(coverityEnvironment, coverityRunConfiguration);
                    coverityCommandService.runCommands(coverityEnvironment, coverityCommands, "", onCommandFailure);
                }

                if (checkForIssuesInView != null) {
                    ViewReportWrapper viewReportWrapper = issuesInViewService.getIssuesInView(coverityInstanceUrl, credentialsId, projectName, viewName);
                    logger.alwaysLog("Checking for issues in view");
                    logger.alwaysLog("-- Build state for issues in the view: " + buildStatus.getDisplayName());
                    logger.alwaysLog("-- Coverity project name: " + projectName);
                    logger.alwaysLog("-- Coverity view name: " + viewName);

                    ViewContents viewContents = viewReportWrapper.getViewContents();
                    String viewReportUrl = viewReportWrapper.getViewReportUrl();
                    int defectCount = viewContents.getTotalRows().intValue();
                    coverityBuildService.addAction(new IssueReportAction(defectCount, viewReportUrl));
                    logger.alwaysLog(String.format("[Coverity] Found %s issues: %s", defectCount, viewReportUrl));

                    if (defectCount > 0) {
                        coverityBuildService.markBuildAs(buildStatus);
                    }
                }
            } finally {
                if (StringUtils.isNotBlank(authKeyFilePath)) {
                    cleanUpWorkflowService.cleanUpAuthenticationFile(authKeyFilePath);
                }

                if (CleanUpAction.DELETE_INTERMEDIATE_DIRECTORY.equals(cleanUpAction)) {
                    cleanUpWorkflowService.cleanUpIntermediateDirectory(intermediateDirectoryPath);
                }
            }
        } catch (InterruptedException e) {
            logger.error("[ERROR] Synopsys Coverity thread was interrupted.", e);
            coverityBuildService.markBuildInterrupted();
            Thread.currentThread().interrupt();
        } catch (IntegrationException e) {
            coverityBuildService.markBuildFailed(e);
        } catch (Exception e) {
            coverityBuildService.markBuildUnstable(e);
        }
    }


}
