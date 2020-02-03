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

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.jenkins.coverity.ChangeSetFilter;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.LogLevel;
import com.synopsys.integration.rest.RestConstants;
import com.synopsys.integration.stepworkflow.AbstractSupplyingSubStep;
import com.synopsys.integration.stepworkflow.SubStepResponse;

import hudson.scm.ChangeLogSet;

public class ProcessChangeLogSets extends AbstractSupplyingSubStep<List<String>> {
    private final IntLogger logger;
    private final List<ChangeLogSet<?>> changeLogSets;
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;

    public ProcessChangeLogSets(final IntLogger logger, final List<ChangeLogSet<?>> changeLogSets, final ConfigureChangeSetPatterns configureChangeSetPatterns) {
        this.logger = logger;
        this.changeLogSets = changeLogSets;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
    }

    @Override
    public SubStepResponse<List<String>> run() {
        logger.debug("Computing $CHANGE_SET");
        final ChangeSetFilter changeSetFilter;
        if (configureChangeSetPatterns == null) {
            changeSetFilter = ChangeSetFilter.createAcceptAllFilter();
            logger.alwaysLog("-- No change set inclusion or exclusion patterns set");
        } else {
            changeSetFilter = configureChangeSetPatterns.createChangeSetFilter();
            logger.alwaysLog("-- Change set inclusion patterns: " + configureChangeSetPatterns.getChangeSetInclusionPatterns());
            logger.alwaysLog("-- Change set exclusion patterns: " + configureChangeSetPatterns.getChangeSetExclusionPatterns());
        }

        final List<String> changeSet = changeLogSets.stream()
                                           .filter(changeLogSet -> !changeLogSet.isEmptySet())
                                           .flatMap(this::toEntries)
                                           .peek(this::logEntry)
                                           .flatMap(this::toAffectedFiles)
                                           .filter(changeSetFilter::shouldInclude)
                                           .map(ChangeLogSet.AffectedFile::getPath)
                                           .filter(StringUtils::isNotBlank)
                                           .collect(Collectors.toList());

        logger.alwaysLog("Computed a $CHANGE_SET of " + changeSet.size() + " files");

        return SubStepResponse.SUCCESS(changeSet);
    }

    private Stream<? extends ChangeLogSet.Entry> toEntries(final ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet) {
        return StreamSupport.stream(changeLogSet.spliterator(), false);
    }

    private Stream<? extends ChangeLogSet.AffectedFile> toAffectedFiles(final ChangeLogSet.Entry entry) {
        return entry.getAffectedFiles().stream();
    }

    private void logEntry(final ChangeLogSet.Entry entry) {
        if (logger.getLogLevel().isLoggable(LogLevel.DEBUG)) {
            final Date date = new Date(entry.getTimestamp());
            logger.debug(String.format("Commit %s by %s on %s: %s", entry.getCommitId(), entry.getAuthor(), RestConstants.formatDate(date), entry.getMsg()));
        }
    }

}
