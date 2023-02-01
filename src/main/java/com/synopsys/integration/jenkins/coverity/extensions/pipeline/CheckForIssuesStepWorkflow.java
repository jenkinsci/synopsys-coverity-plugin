/*
 * synopsys-coverity
 *
 * Copyright (c) 2023 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.pipeline;

import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewReportWrapper;
import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.coverity.actions.IssueReportAction;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityJenkinsStepWorkflow;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.stepworkflow.StepWorkflow;
import com.synopsys.integration.stepworkflow.SubStep;

import hudson.AbortException;
import hudson.model.Result;
import hudson.model.Run;

public class CheckForIssuesStepWorkflow extends CoverityJenkinsStepWorkflow<Integer> {
    private final CoverityWorkflowStepFactory coverityWorkflowStepFactory;
    private final String coverityInstanceUrl;
    private final String credentialsId;
    private final String projectName;
    private final String viewName;
    private final Boolean returnIssueCount;
    private final Boolean markUnstable;
    private final Run<?, ?> run;
    private final FlowNode flowNode;

    public CheckForIssuesStepWorkflow(JenkinsIntLogger jenkinsIntLogger, JenkinsVersionHelper jenkinsVersionHelper, ThrowingSupplier<WebServiceFactory, CoverityJenkinsAbortException> webServiceFactorySupplier,
        CoverityWorkflowStepFactory coverityWorkflowStepFactory, String coverityInstanceUrl, String credentialsId, String projectName, String viewName, Boolean returnIssueCount, Boolean markUnstable, Run<?, ?> run,
        FlowNode flowNode) {
        super(jenkinsIntLogger, jenkinsVersionHelper, webServiceFactorySupplier);
        this.coverityWorkflowStepFactory = coverityWorkflowStepFactory;
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.credentialsId = credentialsId;
        this.projectName = projectName;
        this.viewName = viewName;
        this.returnIssueCount = returnIssueCount;
        this.markUnstable = markUnstable;
        this.run = run;
        this.flowNode = flowNode;
    }

    @Override
    protected StepWorkflow<Integer> buildWorkflow() throws AbortException {
        return StepWorkflow.first(coverityWorkflowStepFactory.createStepGetIssuesInView(coverityInstanceUrl, credentialsId, projectName, viewName))
                   .then(SubStep.ofFunction(this::getDefectCount))
                   .build();
    }

    @Override
    public Integer perform() throws Exception {
        return runWorkflow().getDataOrThrowException();
    }

    private Integer getDefectCount(ViewReportWrapper viewReportWrapper) throws CoverityJenkinsException {
        String viewReportUrl = viewReportWrapper.getViewReportUrl();
        int defectCount = viewReportWrapper.getViewContents().getTotalRows().intValue();
        String defectMessage = String.format("[Coverity] Found %s issues: %s", defectCount, viewReportUrl);
        run.addAction(new IssueReportAction(defectCount, viewReportUrl));

        if (defectCount > 0) {
            if (Boolean.TRUE.equals(markUnstable)) {
                logger.warn(defectMessage);
                flowNode.addOrReplaceAction(new WarningAction(Result.UNSTABLE).withMessage(defectMessage));
                run.setResult(Result.UNSTABLE);
            } else if (Boolean.TRUE.equals(returnIssueCount)) {
                logger.error(defectMessage);
            } else {
                throw new CoverityJenkinsException(defectMessage);
            }
        }

        return defectCount;
    }

    @Override
    protected void cleanUp() throws CoverityJenkinsAbortException {
        // Nothing to clean up
    }
}
