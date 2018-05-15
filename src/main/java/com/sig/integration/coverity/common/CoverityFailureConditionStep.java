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
import com.sig.integration.coverity.ws.v9.ConfigurationService;
import com.sig.integration.coverity.ws.v9.MergedDefectDataObj;
import com.sig.integration.coverity.ws.v9.MergedDefectFilterSpecDataObj;
import com.sig.integration.coverity.ws.v9.ProjectDataObj;
import com.sig.integration.coverity.ws.v9.ProjectFilterSpecDataObj;
import com.sig.integration.coverity.ws.v9.StreamDataObj;

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

            ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            final List<ProjectDataObj> projects = configurationService.getProjects(new ProjectFilterSpecDataObj());

            for (ProjectDataObj project : projects) {
                logger.info("Coverity Project ID: " + project.getId().getName());

                for (StreamDataObj stream : project.getStreams()) {
                    logger.info("Stream: " + stream.getId().getName());
                }

                logger.info("   ");
                logger.info("   ");
                logger.info("   ");
            }

            DefectServiceWrapper defectServiceWrapper = webServiceFactory.createDefectServiceWrapper();
            List<MergedDefectDataObj> mergedDefectDataObjs = defectServiceWrapper.getDefectsForStreams(streamName, new MergedDefectFilterSpecDataObj());
            for (MergedDefectDataObj defect : mergedDefectDataObjs) {
                logger.info(String.format("Defect : %s %s %s %s %s %s ", defect.getCheckerName(), defect.getCid(), defect.getComponentName(), defect.getCwe(), defect.getDisplayIssueKind(), defect.getIssueKind()));
            }
            if (failOnQualityIssues && mergedDefectDataObjs.size() > 0) {
                getRun().setResult(buildStateOnFailure.getResult());
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
