/**
 * synopsys-coverity
 *
 * Copyright (c) 2020 Synopsys, Inc.
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
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.coverity.executable.CoverityToolEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.ChangeSetFilter;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.log.LogLevel;
import com.synopsys.integration.rest.RestConstants;
import com.synopsys.integration.stepworkflow.AbstractConsumingSubStep;
import com.synopsys.integration.stepworkflow.SubStepResponse;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.scm.ChangeLogSet;

public class SetUpCoverityEnvironment extends AbstractConsumingSubStep<String> {
    private final CoverityJenkinsIntLogger logger;
    private final IntEnvironmentVariables intEnvironmentVariables;
    private final List<ChangeLogSet<?>> changeLogSets;
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;
    private final String coverityInstanceUrl;
    private final String coverityUsername;
    private final String coverityPassword;
    private final String projectName;
    private final String streamName;
    private final String viewName;
    private final String intermediateDirectoryPath;
    private final String coverityToolHomeBin;

    public SetUpCoverityEnvironment(CoverityJenkinsIntLogger logger, IntEnvironmentVariables intEnvironmentVariables, List<ChangeLogSet<?>> changeLogSets, ConfigureChangeSetPatterns configureChangeSetPatterns, String coverityInstanceUrl,
        String coverityUsername, String coverityPassphrase, String projectName, String streamName, String viewName, String intermediateDirectoryPath, String coverityToolHomeBin) {
        this.logger = logger;
        this.intEnvironmentVariables = intEnvironmentVariables;
        this.changeLogSets = changeLogSets;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.coverityUsername = coverityUsername;
        this.coverityPassword = coverityPassphrase;
        this.projectName = projectName;
        this.streamName = streamName;
        this.viewName = viewName;
        this.intermediateDirectoryPath = intermediateDirectoryPath;
        this.coverityToolHomeBin = coverityToolHomeBin;
    }

    @Override
    public SubStepResponse<Object> run(String authKeyFilePath) {
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
        intEnvironmentVariables.put(CoverityToolEnvironmentVariable.USER.toString(), coverityUsername);
        intEnvironmentVariables.put(CoverityToolEnvironmentVariable.PASSPHRASE.toString(), coverityPassword);
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
        logger.alwaysLog("-- Synopsys Coverity username: " + coverityUsername);
        Arrays.stream(JenkinsCoverityEnvironmentVariable.values())
            .map(JenkinsCoverityEnvironmentVariable::toString)
            .map(environmentVariable -> String.format("-- $%s=%s", environmentVariable, intEnvironmentVariables.getValue(environmentVariable)))
            .forEach(logger::alwaysLog);

        return SubStepResponse.SUCCESS();
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
