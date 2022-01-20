/*
 * synopsys-coverity
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.stepworkflow.AbstractConsumingSubStep;
import com.synopsys.integration.stepworkflow.SubStepResponse;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.remoting.VirtualChannel;

public class RunCoverityCommands extends AbstractConsumingSubStep<List<List<String>>> {
    private final CoverityJenkinsIntLogger logger;
    private final IntEnvironmentVariables intEnvironmentVariables;
    private final String remoteWorkingDirectory;
    private final OnCommandFailure onCommandFailure;
    private final VirtualChannel virtualChannel;

    public RunCoverityCommands(final CoverityJenkinsIntLogger logger, final IntEnvironmentVariables intEnvironmentVariables, final String remoteWorkingDirectory, final OnCommandFailure onCommandFailure,
        final VirtualChannel virtualChannel) {
        this.logger = logger;
        this.intEnvironmentVariables = intEnvironmentVariables;
        this.remoteWorkingDirectory = remoteWorkingDirectory;
        this.onCommandFailure = onCommandFailure;
        this.virtualChannel = virtualChannel;
    }

    public SubStepResponse<Object> run(final List<List<String>> commands) {
        try {
            boolean oneOrMoreCommandsFailed = false;
            for (final List<String> arguments : commands) {
                if (arguments.isEmpty()) {
                    continue;
                }

                final CoverityRemoteToolRunner coverityRemoteToolRunner = new CoverityRemoteToolRunner(logger, intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME.toString()), arguments,
                    remoteWorkingDirectory, (HashMap<String, String>) intEnvironmentVariables.getVariables());

                final Integer exitCode = virtualChannel.call(coverityRemoteToolRunner);

                if (exitCode != null && exitCode != 0) {
                    final String exitCodeErrorMessage = "Coverity failed with exit code: " + exitCode;

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
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return SubStepResponse.FAILURE(e);
        } catch (final IOException | IntegrationException e) {
            return SubStepResponse.FAILURE(e);
        }

        return SubStepResponse.SUCCESS();
    }

}
