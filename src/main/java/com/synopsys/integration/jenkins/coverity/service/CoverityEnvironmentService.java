/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.coverity.executable.CoverityToolEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.ChangeSetFilter;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.log.LogLevel;
import com.synopsys.integration.rest.RestConstants;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.scm.ChangeLogSet;

public class CoverityEnvironmentService {
    private final JenkinsIntLogger logger;
    private final CoverityConfigService coverityConfigService;
    private final Map<String, String> environmentVariables;

    public CoverityEnvironmentService(JenkinsIntLogger logger, CoverityConfigService coverityConfigService, Map<String, String> environmentVariables) {
        this.logger = logger;
        this.coverityConfigService = coverityConfigService;
        this.environmentVariables = environmentVariables;
    }

    public IntEnvironmentVariables createCoverityEnvironment(List<ChangeLogSet<?>> changeLogSets, ConfigureChangeSetPatterns configureChangeSetPatterns, String coverityInstanceUrl, String credentialsId, String projectName, String streamName, String viewName, String intermediateDirectoryPath, String coverityToolHomeBin, String authKeyFilePath) throws CoverityJenkinsAbortException {
        CoverityConnectInstance coverityConnectInstance = coverityConfigService.getCoverityInstanceOrAbort(coverityInstanceUrl);

        IntEnvironmentVariables intEnvironmentVariables = IntEnvironmentVariables.empty();
        intEnvironmentVariables.putAll(environmentVariables);
        logger.setLogLevel(intEnvironmentVariables);

        logger.debug("Computing $CHANGE_SET");
        ChangeSetFilter changeSetFilter;
        if (configureChangeSetPatterns == null) {
            changeSetFilter = ChangeSetFilter.createAcceptAllFilter();
            logger.alwaysLog("-- No change set inclusion or exclusion patterns set");
        } else {
            changeSetFilter = configureChangeSetPatterns.createChangeSetFilter();
            logger.alwaysLog("-- Change set inclusion patterns: " + configureChangeSetPatterns.getChangeSetInclusionPatterns());
            logger.alwaysLog("-- Change set exclusion patterns: " + configureChangeSetPatterns.getChangeSetExclusionPatterns());
        }

        List<String> changeSet = changeLogSets.stream()
                                     .filter(changeLogSet -> !changeLogSet.isEmptySet())
                                     .flatMap(this::toEntries)
                                     .peek(this::logEntry)
                                     .flatMap(this::toAffectedFiles)
                                     .filter(changeSetFilter::shouldInclude)
                                     .map(ChangeLogSet.AffectedFile::getPath)
                                     .filter(StringUtils::isNotBlank)
                                     .collect(Collectors.toList());

        logger.alwaysLog("Computed a $CHANGE_SET of " + changeSet.size() + " files");

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

    private Stream<? extends ChangeLogSet.Entry> toEntries(ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet) {
        return StreamSupport.stream(changeLogSet.spliterator(), false);
    }

    private Stream<? extends ChangeLogSet.AffectedFile> toAffectedFiles(ChangeLogSet.Entry entry) {
        return entry.getAffectedFiles().stream();
    }

    private void logEntry(ChangeLogSet.Entry entry) {
        if (logger.getLogLevel().isLoggable(LogLevel.DEBUG)) {
            Date date = new Date(entry.getTimestamp());
            logger.debug(String.format("Commit %s by %s on %s: %s", entry.getCommitId(), entry.getAuthor(), RestConstants.formatDate(date), entry.getMsg()));
        }

    }
}
