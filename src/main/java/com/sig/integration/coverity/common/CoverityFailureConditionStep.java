/**
 * sig-coverity
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sig.integration.coverity.common;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.blackducksoftware.integration.log.IntLogger;
import com.sig.integration.coverity.JenkinsCoverityInstance;
import com.sig.integration.coverity.JenkinsCoverityLogger;
import com.sig.integration.coverity.config.CoverityServerConfig;
import com.sig.integration.coverity.config.CoverityServerConfigBuilder;
import com.sig.integration.coverity.ws.DefectServiceWrapper;
import com.sig.integration.coverity.ws.WebServiceFactory;
import com.sig.integration.coverity.ws.v9.MergedDefectDataObj;
import com.sig.integration.coverity.ws.v9.MergedDefectFilterSpecDataObj;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class CoverityFailureConditionStep extends BaseCoverityStep {

    public CoverityFailureConditionStep(Node node, TaskListener listener, EnvVars envVars, FilePath workspace, Run run) {
        super(node, listener, envVars, workspace, run);
    }

    public boolean runCommonCoverityFailureStep(Optional<String> optionalBuildStateOnFailure, Optional<Boolean> optionalFailOnQualityIssues, Optional<Boolean> optionalFailOnSecurityIssues, Optional<String> optionalStreamName) {
        final JenkinsCoverityLogger logger = createJenkinsCoverityLogger();
        try {
            if (!shouldRunFailureStep(logger, optionalBuildStateOnFailure, optionalFailOnQualityIssues, optionalFailOnSecurityIssues, optionalStreamName)) {
                logger.warn("Skipping SIG Coverity failure condition check.");
                return false;
            }
            JenkinsCoverityInstance coverityInstance = getCoverityInstance();
            if (!validateFailureStepConfiguration(logger, optionalBuildStateOnFailure, optionalFailOnQualityIssues, optionalFailOnSecurityIssues, optionalStreamName, coverityInstance, getRun())) {
                logger.error("SIG Coverity failure condition configuration is invalid.");
                return false;
            }

            String buildStateOnFailureString = optionalBuildStateOnFailure.orElse("");
            BuildState buildStateOnFailure = BuildState.getBuildStateFromDisplayValue(buildStateOnFailureString).orElse(BuildState.FAILURE);
            Boolean failOnQualityIssues = optionalFailOnQualityIssues.orElse(false);
            Boolean failOnSecurityIssues = optionalFailOnSecurityIssues.orElse(false);
            String streamName = handleVariableReplacement(getEnvVars(), optionalStreamName.orElse(""));

            logger.alwaysLog("Checking SIG Coverity Failure conditions.");

            logGlobalConfiguration(coverityInstance, logger);
            logFailureConditionConfiguration(buildStateOnFailure, failOnQualityIssues, failOnSecurityIssues, streamName, logger);

            CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
            URL coverityURL = coverityInstance.getCoverityURL().get();
            builder.url(coverityURL.toString());
            builder.username(coverityInstance.getCoverityUsername().orElse(null));
            builder.password(coverityInstance.getCoverityPassword().orElse(null));

            CoverityServerConfig coverityServerConfig = builder.build();
            WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, logger);
            webServiceFactory.connect();

            DefectServiceWrapper defectServiceWrapper = webServiceFactory.createDefectServiceWrapper();
            List<MergedDefectDataObj> mergedDefectDataObjs = defectServiceWrapper.getDefectsForStream(streamName, new MergedDefectFilterSpecDataObj());
            Boolean foundQualityIssue = false;
            Boolean foundSecurityIssue = false;
            for (MergedDefectDataObj defect : mergedDefectDataObjs) {
                if (failOnQualityIssues && !foundQualityIssue && null != defect.getIssueKind() && defect.getIssueKind().toUpperCase().equals("QUALITY")) {
                    logger.warn(String.format("Setting the Build Result to %s because a quality issue was found for the stream %s", buildStateOnFailure.getDisplayValue(), streamName));
                    getRun().setResult(buildStateOnFailure.getResult());
                    foundQualityIssue = true;
                } else if (failOnSecurityIssues && !foundSecurityIssue && null != defect.getIssueKind() && defect.getIssueKind().toUpperCase().equals("SECURITY")) {
                    logger.warn(String.format("Setting the Build Result to %s because a security issue was found for the stream %s", buildStateOnFailure.getDisplayValue(), streamName));
                    foundSecurityIssue = true;
                    getRun().setResult(buildStateOnFailure.getResult());
                }
                if (failOnQualityIssues && foundQualityIssue && !failOnSecurityIssues) {
                    // If they only want to fail on Quality issues and we found one then lets exit the loop
                    break;
                } else if (failOnSecurityIssues && foundSecurityIssue && !failOnQualityIssues) {
                    // If they only want to fail on Security issues and we found one then lets exit the loop
                    break;
                } else if (failOnQualityIssues && foundQualityIssue && failOnSecurityIssues && foundSecurityIssue) {
                    // If they want to fail on Quality and Security issues and we found both then lets exit the loop
                    break;
                }
            }
        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage(), e);
            getRun().setResult(Result.UNSTABLE);
            return false;
        }
        return true;
    }

    private boolean shouldRunFailureStep(JenkinsCoverityLogger logger, Optional<String> optionalBuildStateOnFailure, Optional<Boolean> optionalFailOnQualityIssues, Optional<Boolean> optionalFailOnSecurityIssues,
            Optional<String> optionalStreamName) {
        Boolean failOnQualityIssues = optionalFailOnQualityIssues.orElse(false);
        Boolean failOnSecurityIssues = optionalFailOnSecurityIssues.orElse(false);
        Boolean shouldContinue = true;
        if (!optionalBuildStateOnFailure.isPresent()) {
            logger.debug("Missing build state to set on failure.");
            shouldContinue = false;
        }
        if ((!optionalFailOnQualityIssues.isPresent() || !failOnQualityIssues) && (!optionalFailOnSecurityIssues.isPresent() || !failOnSecurityIssues)) {
            logger.debug("No failure condition is configured to check.");
            shouldContinue = false;
        }
        if (!optionalStreamName.isPresent()) {
            logger.warn("There was no Coverity stream name provided.");
            shouldContinue = false;
        }
        return shouldContinue;
    }

    private Boolean validateFailureStepConfiguration(JenkinsCoverityLogger logger, Optional<String> optionalBuildStateOnFailure, Optional<Boolean> optionalFailOnQualityIssues, Optional<Boolean> optionalFailOnSecurityIssues,
            Optional<String> optionalStreamName, JenkinsCoverityInstance coverityInstance, Run run) {
        String buildStateOnFailure = optionalBuildStateOnFailure.orElse("");
        String streamName = optionalStreamName.orElse("");
        Boolean shouldContinue = true;

        Optional<BuildState> optionalBuildState = BuildState.getBuildStateFromDisplayValue(buildStateOnFailure);
        if (!optionalBuildState.isPresent()) {
            logger.error("The provided build state to set on failure is not valid. " + buildStateOnFailure);
            shouldContinue = false;
            run.setResult(Result.FAILURE);
        }
        if (StringUtils.isBlank(streamName)) {
            logger.warn("No Coverity stream name provided. ");
            shouldContinue = false;
            run.setResult(Result.FAILURE);
        }

        if (null == coverityInstance) {
            logger.error("No global SIG Coverity configuration found.");
            shouldContinue = false;
            run.setResult(Result.FAILURE);
        } else {
            Optional<URL> optionalCoverityURL = coverityInstance.getCoverityURL();
            if (!optionalCoverityURL.isPresent()) {
                logger.error("No Coverity URL configured.");
                shouldContinue = false;
                run.setResult(Result.FAILURE);
            }
            Optional<String> optionalCoverityUsername = coverityInstance.getCoverityUsername();
            if (!optionalCoverityUsername.isPresent()) {
                logger.error("No Coverity Username configured.");
                shouldContinue = false;
                run.setResult(Result.FAILURE);
            }
            Optional<String> optionalCoverityPassword = coverityInstance.getCoverityPassword();
            if (!optionalCoverityPassword.isPresent()) {
                logger.error("No Coverity Password configured.");
                shouldContinue = false;
                run.setResult(Result.FAILURE);
            }
        }
        return shouldContinue;
    }

    private void logFailureConditionConfiguration(BuildState buildState, Boolean failOnQualityIssues, Boolean failOnSecurityIssues, String streamName, IntLogger logger) {
        logger.alwaysLog("-- Build State on Failure Condition : " + buildState.getDisplayValue());
        logger.alwaysLog("-- Fail on quality issues : " + failOnQualityIssues);
        logger.alwaysLog("-- Fail on security issues : " + failOnSecurityIssues);
        logger.alwaysLog("-- Coverity stream name : " + streamName);
    }

}
