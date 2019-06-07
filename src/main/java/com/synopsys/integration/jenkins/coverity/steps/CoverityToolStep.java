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
package com.synopsys.integration.jenkins.coverity.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.types.Commandline;

import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityCaptureType;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.AdvancedCoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CommandArguments;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.RepeatableCommand;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.SimpleCoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.steps.remote.CoverityRemoteToolRunner;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class CoverityToolStep extends BaseCoverityStep {
    public CoverityToolStep(final Node node, final TaskListener listener, final EnvVars envVars, final FilePath workspace, final Run run) {
        super(node, listener, envVars, workspace, run);
    }

    public boolean runCoverityToolStep(final CoverityRunConfiguration coverityRunConfiguration, final OnCommandFailure onCommandFailure) {
        initializeJenkinsCoverityLogger();
        final RepeatableCommand[] commands;

        try {
            if (CoverityRunConfiguration.RunConfigurationType.ADVANCED.equals(coverityRunConfiguration.getRunConFigurationType())) {
                commands = ((AdvancedCoverityRunConfiguration) coverityRunConfiguration).getCommands();
            } else {
                commands = this.getSimpleModeCommands((SimpleCoverityRunConfiguration) coverityRunConfiguration);
            }

            if (Result.ABORTED == getResult()) {
                logger.alwaysLog("Skipping the Synopsys Coverity step because the build was aborted.");
                return false;
            }

            if (!verifyCoverityCommands(commands)) {
                setResult(Result.FAILURE);
                return false;
            }

            for (final RepeatableCommand repeatableCommand : commands) {
                final String command = repeatableCommand.getCommand();
                if (StringUtils.isBlank(command)) {
                    continue;
                }

                final List<String> arguments = getCorrectedParameters(command);

                final CoverityRemoteToolRunner coverityRemoteToolRunner = new CoverityRemoteToolRunner(logger, getEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME), arguments, getWorkspace().getRemote(),
                    getEnvVars());
                final Integer exitCode = getNode().getChannel().call(coverityRemoteToolRunner);
                boolean shouldStop = false;
                if (exitCode != null && exitCode != 0) {
                    logger.error("[ERROR] Coverity failed with exit code: " + exitCode);
                    setResult(Result.FAILURE);
                    shouldStop = true;
                }
                if (OnCommandFailure.SKIP_REMAINING_COMMANDS.equals(onCommandFailure) && shouldStop) {
                    break;
                }
            }
        } catch (final InterruptedException e) {
            logger.error("[ERROR] Synopsys Coverity thread was interrupted.", e);
            setResult(Result.ABORTED);
            Thread.currentThread().interrupt();
            return false;

        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage());
            logger.debug(null, e);
            setResult(Result.UNSTABLE);
            return false;
        }
        return true;
    }

    private RepeatableCommand[] getSimpleModeCommands(final SimpleCoverityRunConfiguration simpleCoverityRunConfiguration) throws CoverityJenkinsException {
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
        } else if (coverityCaptureType == CoverityCaptureType.COV_BUILD) {
            repeatableCommands[0] = RepeatableCommand.COV_BUILD(simpleCoverityRunConfiguration.getSourceArgument(), covBuildArguments);
        } else {
            throw new CoverityJenkinsException("No valid Coverity capture type specified.");
        }

        final CoverityAnalysisType coverityAnalysisType = simpleCoverityRunConfiguration.getCoverityAnalysisType();

        if (coverityAnalysisType == CoverityAnalysisType.COV_ANALYZE) {
            repeatableCommands[1] = RepeatableCommand.COV_ANALYZE(covAnalyzeArguments);
        } else if (coverityAnalysisType == CoverityAnalysisType.COV_RUN_DESKTOP) {
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

    private boolean verifyCoverityCommands(final RepeatableCommand[] commands) {
        if (commands.length == 0) {
            logger.error("[ERROR] There are no Coverity commands configured to run.");
            return false;
        }

        if (Arrays.stream(commands).map(RepeatableCommand::getCommand).allMatch(StringUtils::isBlank)) {
            logger.error("[ERROR] The are no non-empty Coverity commands configured.");
            return false;
        }

        return true;
    }

    private List<String> getCorrectedParameters(final String command) {
        final String[] separatedParameters = Commandline.translateCommandline(command);
        final List<String> correctedParameters = new ArrayList<>();
        for (final String parameter : separatedParameters) {
            correctedParameters.add(handleVariableReplacement(getEnvVars(), parameter));
        }

        return correctedParameters;
    }

}
