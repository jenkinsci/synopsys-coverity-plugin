/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.synopsys.integration.coverity.executable.CoverityToolEnvironmentVariable;
import com.synopsys.integration.jenkins.ChangeSetFilter;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsScmService;
import com.synopsys.integration.util.IntEnvironmentVariables;

public class CoverityEnvironmentService {
    private final JenkinsIntLogger logger;
    private final CoverityConfigService coverityConfigService;
    private final Map<String, String> environmentVariables;
    private final JenkinsScmService jenkinsScmService;

    public CoverityEnvironmentService(JenkinsIntLogger logger, CoverityConfigService coverityConfigService, Map<String, String> environmentVariables, JenkinsScmService jenkinsScmService) {
        this.logger = logger;
        this.coverityConfigService = coverityConfigService;
        this.environmentVariables = environmentVariables;
        this.jenkinsScmService = jenkinsScmService;
    }

    public String getAuthKeyFilePath() {
        return environmentVariables.get(JenkinsCoverityEnvironmentVariable.TEMPORARY_AUTH_KEY_PATH.toString());
    }

    public String getCoverityToolHome() {
        return environmentVariables.get(JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME.toString());
    }

    public IntEnvironmentVariables createCoverityEnvironment(ConfigureChangeSetPatterns configureChangeSetPatterns, String coverityInstanceUrl, String credentialsId, String projectName, String streamName, String viewName, String intermediateDirectoryPath, String coverityToolHomeBin, String authKeyFilePath) throws CoverityJenkinsAbortException {
        CoverityConnectInstance coverityConnectInstance = coverityConfigService.getCoverityInstanceOrAbort(coverityInstanceUrl);

        IntEnvironmentVariables intEnvironmentVariables = IntEnvironmentVariables.empty();
        intEnvironmentVariables.putAll(environmentVariables);
        logger.setLogLevel(intEnvironmentVariables);

        logger.debug("Computing $CHANGE_SET");
        ChangeSetFilter changeSetFilter = new ChangeSetFilter(logger);
        if (configureChangeSetPatterns == null) {
            logger.alwaysLog("-- No change set inclusion or exclusion patterns set");
        } else {
            String inclusionPatterns = configureChangeSetPatterns.getChangeSetInclusionPatterns();
            logger.alwaysLog("-- Change set inclusion patterns: " + inclusionPatterns);
            changeSetFilter.includeMatching(inclusionPatterns);

            String exclusionPatterns = configureChangeSetPatterns.getChangeSetExclusionPatterns();
            logger.alwaysLog("-- Change set exclusion patterns: " + exclusionPatterns);
            changeSetFilter.excludeMatching(exclusionPatterns);
        }

        List<String> changeSet;
        try {
            changeSet = jenkinsScmService.getFilePathsFromChangeSet(changeSetFilter);
            logger.alwaysLog("Computed a $CHANGE_SET of " + changeSet.size() + " files");
        } catch (Exception e) {
            logger.warn(String.format("WARNING: Synopsys Coverity for Jenkins could not determine the change set, %s will be empty and %s will be 0.",
                JenkinsCoverityEnvironmentVariable.CHANGE_SET,
                JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE));

            changeSet = Collections.emptyList();
        }

        intEnvironmentVariables.put("PATH+COVERITYTOOLBIN", coverityToolHomeBin);
        coverityConnectInstance
            .getUsername(logger, credentialsId)
            .ifPresent(username -> intEnvironmentVariables.put(CoverityToolEnvironmentVariable.USER.toString(), username));
        coverityConnectInstance
            .getPassphrase(credentialsId)
            .ifPresent(passphrase -> intEnvironmentVariables.put(CoverityToolEnvironmentVariable.PASSPHRASE.toString(), passphrase));
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.TEMPORARY_AUTH_KEY_PATH.toString(), authKeyFilePath);
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_URL.toString(), coverityInstanceUrl);
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_PROJECT.toString(), projectName);
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_STREAM.toString(), streamName);
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_VIEW.toString(), viewName);
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.CHANGE_SET.toString(), String.join(" ", changeSet));
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE.toString(), String.valueOf(changeSet.size()));
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_INTERMEDIATE_DIRECTORY.toString(), intermediateDirectoryPath);

        logger.alwaysLog("Synopsys Coverity environment:");
        logger.alwaysLog("-- Synopsys Coverity static analysis tool home: " + coverityToolHomeBin);
        logger.alwaysLog("-- Synopsys Coverity username: " + intEnvironmentVariables.getValue(CoverityToolEnvironmentVariable.USER.toString()));
        Arrays.stream(JenkinsCoverityEnvironmentVariable.values())
            .map(JenkinsCoverityEnvironmentVariable::toString)
            .map(environmentVariable -> String.format("-- $%s=%s", environmentVariable, intEnvironmentVariables.getValue(environmentVariable)))
            .forEach(logger::alwaysLog);

        return intEnvironmentVariables;
    }
}
