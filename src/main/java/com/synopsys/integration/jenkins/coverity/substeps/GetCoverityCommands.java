/**
 * synopsys-coverity
 *
 * Copyright (c) 2019 Synopsys, Inc.
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
package com.synopsys.integration.jenkins.coverity.substeps;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.types.Commandline;

import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityCaptureType;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.AdvancedCoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CommandArguments;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.RepeatableCommand;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.SimpleCoverityRunConfiguration;
import com.synopsys.integration.jenkins.substeps.AbstractSupplyingSubStep;
import com.synopsys.integration.jenkins.substeps.SubStepResponse;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.Util;

public class GetCoverityCommands extends AbstractSupplyingSubStep<Void, List<List<String>>> {
    private final JenkinsCoverityLogger logger;
    private final IntEnvironmentVariables intEnvironmentVariables;
    private final CoverityRunConfiguration coverityRunConfiguration;

    public GetCoverityCommands(final JenkinsCoverityLogger logger, final IntEnvironmentVariables intEnvironmentVariables, final CoverityRunConfiguration coverityRunConfiguration) {
        this.logger = logger;
        this.intEnvironmentVariables = intEnvironmentVariables;
        this.coverityRunConfiguration = coverityRunConfiguration;
    }

    public SubStepResponse<List<List<String>>> run() {
        logger.debug("Preparing Coverity commands");
        try {
            final RepeatableCommand[] commands;
            final int changeSetSize = Integer.valueOf(intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE.toString()));

            if (CoverityRunConfiguration.RunConfigurationType.ADVANCED.equals(coverityRunConfiguration.getRunConFigurationType())) {
                commands = ((AdvancedCoverityRunConfiguration) coverityRunConfiguration).getCommands();
            } else {
                commands = this.getSimpleModeCommands((SimpleCoverityRunConfiguration) coverityRunConfiguration, changeSetSize);
            }

            if (Arrays.stream(commands).map(RepeatableCommand::getCommand).allMatch(StringUtils::isBlank)) {
                throw new CoverityJenkinsException("[ERROR] The are no non-empty Coverity commands configured.");
            }

            return Arrays.stream(commands)
                       .map(RepeatableCommand::getCommand)
                       .filter(StringUtils::isNotBlank)
                       .map(this::toParameters)
                       .collect(Collectors.collectingAndThen(Collectors.toList(), SubStepResponse::SUCCESS));
        } catch (final CoverityJenkinsException e) {
            return SubStepResponse.FAILURE(e);
        }
    }

    private RepeatableCommand[] getSimpleModeCommands(final SimpleCoverityRunConfiguration simpleCoverityRunConfiguration, final int changeSetSize) throws CoverityJenkinsException {
        final RepeatableCommand[] repeatableCommands = new RepeatableCommand[3];

        final CommandArguments commandArguments = simpleCoverityRunConfiguration.getCommandArguments();
        final String covBuildArguments = getArgumentsIfAvailable(commandArguments, CommandArguments::getCovBuildArguments);
        final String covCaptureArguments = getArgumentsIfAvailable(commandArguments, CommandArguments::getCovCaptureArguments);
        final String covAnalyzeArguments = getArgumentsIfAvailable(commandArguments, CommandArguments::getCovAnalyzeArguments);
        final String covRunDesktopArguments = getArgumentsIfAvailable(commandArguments, CommandArguments::getCovRunDesktopArguments);
        final String covCommitDefectsArguments = getArgumentsIfAvailable(commandArguments, CommandArguments::getCovCommitDefectsArguments);

        final CoverityCaptureType coverityCaptureType = simpleCoverityRunConfiguration.getCoverityCaptureType();
        final String sourceArgument = simpleCoverityRunConfiguration.getSourceArgument();

        if (coverityCaptureType == CoverityCaptureType.COV_CAPTURE_PROJECT) {
            repeatableCommands[0] = RepeatableCommand.COV_CAPTURE_PROJECT(sourceArgument, covCaptureArguments);
        } else if (coverityCaptureType == CoverityCaptureType.COV_CAPTURE_SCM) {
            repeatableCommands[0] = RepeatableCommand.COV_CAPTURE_SCM(sourceArgument, covCaptureArguments);
        } else {
            if (coverityCaptureType != CoverityCaptureType.COV_BUILD) {
                logger.warn("No valid Coverity Capture Type specified. Assuming Capture type of 'Build.' If you're upgrading from a previous version, this warning will persist until you re-save your job config.");
            }
            repeatableCommands[0] = RepeatableCommand.COV_BUILD(sourceArgument, covBuildArguments);
        }

        final CoverityAnalysisType coverityAnalysisType = simpleCoverityRunConfiguration.getCoverityAnalysisType();

        if (coverityAnalysisType == CoverityAnalysisType.COV_ANALYZE || (coverityAnalysisType == CoverityAnalysisType.THRESHOLD && changeSetSize >= simpleCoverityRunConfiguration.getChangeSetAnalysisThreshold())) {
            repeatableCommands[1] = RepeatableCommand.COV_ANALYZE(covAnalyzeArguments);
        } else if (coverityAnalysisType == CoverityAnalysisType.COV_RUN_DESKTOP || coverityAnalysisType == CoverityAnalysisType.THRESHOLD) {
            repeatableCommands[1] = RepeatableCommand.COV_RUN_DESKTOP(covRunDesktopArguments, String.format("${%s}", JenkinsCoverityEnvironmentVariable.CHANGE_SET.toString()));
        } else {
            throw new CoverityJenkinsException("No valid Coverity analysis type specified");
        }

        repeatableCommands[2] = RepeatableCommand.COV_COMMIT_DEFECTS(covCommitDefectsArguments);

        return repeatableCommands;
    }

    private String getArgumentsIfAvailable(final CommandArguments commandArguments, final Function<CommandArguments, String> getter) {
        if (commandArguments == null) {
            return StringUtils.EMPTY;
        } else {
            return getter.apply(commandArguments);
        }
    }

    private List<String> toParameters(final String command) {
        return Arrays.stream(Commandline.translateCommandline(command))
                   .map(parameter -> Util.replaceMacro(parameter, intEnvironmentVariables.getVariables()))
                   .collect(Collectors.toList());
    }

}
