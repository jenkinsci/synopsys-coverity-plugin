/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.types.Commandline;

import com.synopsys.integration.exception.IntegrationException;
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
import com.synopsys.integration.jenkins.coverity.service.callable.CoverityRemoteToolRunner;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsRemotingService;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.Util;

public class CoverityCommandService {
    private final JenkinsIntLogger logger;
    private final JenkinsRemotingService jenkinsRemotingService;

    public CoverityCommandService(JenkinsIntLogger logger, JenkinsRemotingService jenkinsRemotingService) {
        this.logger = logger;
        this.jenkinsRemotingService = jenkinsRemotingService;
    }

    public List<List<String>> getCommands(IntEnvironmentVariables intEnvironmentVariables, CoverityRunConfiguration coverityRunConfiguration) throws CoverityJenkinsException {
        logger.debug("Preparing Coverity commands");
        RepeatableCommand[] commands;
        int changeSetSize = Integer.parseInt(intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE.toString()));

        if (CoverityRunConfiguration.RunConfigurationType.ADVANCED.equals(coverityRunConfiguration.getRunConFigurationType())) {
            commands = ((AdvancedCoverityRunConfiguration) coverityRunConfiguration).getCommands();
        } else {
            String pathToAuthKeyFile = intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.TEMPORARY_AUTH_KEY_PATH.toString());
            commands = this.getSimpleModeCommands((SimpleCoverityRunConfiguration) coverityRunConfiguration, changeSetSize, pathToAuthKeyFile);
        }

        if (Arrays.stream(commands).map(RepeatableCommand::getCommand).allMatch(StringUtils::isBlank)) {
            throw new CoverityJenkinsException("[ERROR] The are no non-empty Coverity commands configured.");
        }

        return Arrays.stream(commands)
                   .map(RepeatableCommand::getCommand)
                   .filter(StringUtils::isNotBlank)
                   .map(command -> this.toParameters(intEnvironmentVariables, command))
                   .collect(Collectors.toList());
    }

    public void run(IntEnvironmentVariables intEnvironmentVariables, List<List<String>> commands, String remoteWorkingDirectory, OnCommandFailure onCommandFailure) throws IntegrationException, IOException, InterruptedException {
        boolean oneOrMoreCommandsFailed = false;
        for (List<String> arguments : commands) {
            if (arguments.isEmpty()) {
                continue;
            }

            CoverityRemoteToolRunner coverityRemoteToolRunner = new CoverityRemoteToolRunner(logger, intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME.toString()), arguments,
                remoteWorkingDirectory, (HashMap<String, String>) intEnvironmentVariables.getVariables());

            Integer exitCode = jenkinsRemotingService.call(coverityRemoteToolRunner);

            if (exitCode != null && exitCode != 0) {
                String exitCodeErrorMessage = "Coverity failed with exit code: " + exitCode;

                if (OnCommandFailure.SKIP_REMAINING_COMMANDS.equals(onCommandFailure)) {
                    throw new CoverityJenkinsException(exitCodeErrorMessage);
                } else {
                    oneOrMoreCommandsFailed = true;
                    logger.error(exitCodeErrorMessage);
                }
            }
        }

        if (oneOrMoreCommandsFailed) {
            throw new CoverityJenkinsException("One or more Coverity commands failed");
        }
    }

    private RepeatableCommand[] getSimpleModeCommands(SimpleCoverityRunConfiguration simpleCoverityRunConfiguration, int changeSetSize, String pathToAuthKeyFile) throws CoverityJenkinsException {
        RepeatableCommand[] repeatableCommands = new RepeatableCommand[3];

        CommandArguments commandArguments = simpleCoverityRunConfiguration.getCommandArguments();
        String covBuildArguments = getArgumentsIfAvailable(commandArguments, CommandArguments::getCovBuildArguments);
        String covCaptureArguments = getArgumentsIfAvailable(commandArguments, CommandArguments::getCovCaptureArguments);
        String covAnalyzeArguments = getArgumentsIfAvailable(commandArguments, CommandArguments::getCovAnalyzeArguments);
        String covRunDesktopArguments = getArgumentsIfAvailable(commandArguments, CommandArguments::getCovRunDesktopArguments);
        String covCommitDefectsArguments = getArgumentsIfAvailable(commandArguments, CommandArguments::getCovCommitDefectsArguments);

        CoverityCaptureType coverityCaptureType = simpleCoverityRunConfiguration.getCoverityCaptureType();
        String sourceArgument = simpleCoverityRunConfiguration.getSourceArgument();

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

        CoverityAnalysisType coverityAnalysisType = simpleCoverityRunConfiguration.getCoverityAnalysisType();

        if (coverityAnalysisType == CoverityAnalysisType.COV_ANALYZE || (coverityAnalysisType == CoverityAnalysisType.THRESHOLD && changeSetSize >= simpleCoverityRunConfiguration.getChangeSetAnalysisThreshold())) {
            repeatableCommands[1] = RepeatableCommand.COV_ANALYZE(covAnalyzeArguments);
        } else if (coverityAnalysisType == CoverityAnalysisType.COV_RUN_DESKTOP || coverityAnalysisType == CoverityAnalysisType.THRESHOLD) {
            repeatableCommands[1] = RepeatableCommand.COV_RUN_DESKTOP(pathToAuthKeyFile, covRunDesktopArguments);
        } else {
            throw new CoverityJenkinsException("No valid Coverity analysis type specified");
        }

        repeatableCommands[2] = RepeatableCommand.COV_COMMIT_DEFECTS(pathToAuthKeyFile, covCommitDefectsArguments);

        return repeatableCommands;
    }

    private String getArgumentsIfAvailable(CommandArguments commandArguments, Function<CommandArguments, String> getter) {
        if (commandArguments == null) {
            return StringUtils.EMPTY;
        } else {
            return getter.apply(commandArguments);
        }
    }

    private List<String> toParameters(IntEnvironmentVariables intEnvironmentVariables, String command) {
        return Arrays.stream(Commandline.translateCommandline(command))
                   .map(parameter -> Util.replaceMacro(parameter, intEnvironmentVariables.getVariables()))
                   .collect(Collectors.toList());
    }

}
