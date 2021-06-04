package com.synopsys.integration.jenkins.coverity;

import java.io.IOException;

import com.synopsys.integration.coverity.api.ws.configuration.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.ws.view.ViewReportWrapper;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.actions.IssueReportAction;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.service.CoverityPhoneHomeService;
import com.synopsys.integration.jenkins.coverity.service.IssuesInViewService;
import com.synopsys.integration.jenkins.coverity.service.common.CoverityRunService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;

public class CoverityPipelineCommands {
    private final JenkinsIntLogger logger;
    private final CoverityRunService coverityRunService;
    private final CoverityPhoneHomeService coverityPhoneHomeService;
    private final IssuesInViewService issuesInViewService;

    public CoverityPipelineCommands(JenkinsIntLogger logger, CoverityRunService coverityRunService, CoverityPhoneHomeService coverityPhoneHomeService, IssuesInViewService issuesInViewService){
        this.logger = logger;
        this.coverityRunService = coverityRunService;
        this.coverityPhoneHomeService = coverityPhoneHomeService;
        this.issuesInViewService = issuesInViewService;
    }

    public int getIssueCount(String coverityInstanceUrl, String credentialsId, String projectName, String viewName, Boolean returnIssueCount) throws IntegrationException, CovRemoteServiceException_Exception, IOException {
        coverityPhoneHomeService.phoneHome(coverityInstanceUrl, credentialsId);

        ViewReportWrapper viewReportWrapper = issuesInViewService.getIssuesInView(coverityInstanceUrl, credentialsId, projectName, viewName);
        String viewReportUrl = viewReportWrapper.getViewReportUrl();
        int defectCount = viewReportWrapper.getViewContents().getTotalRows().intValue();
        String defectMessage = String.format("[Coverity] Found %s issues: %s", defectCount, viewReportUrl);

        coverityRunService.addAction(new IssueReportAction(defectCount, viewReportUrl));
        if (defectCount > 0) {
            if (Boolean.TRUE.equals(returnIssueCount)) {
                logger.error(defectMessage);
            } else {
                throw new CoverityJenkinsException(defectMessage);
            }
        }

        return defectCount;
    }


}
