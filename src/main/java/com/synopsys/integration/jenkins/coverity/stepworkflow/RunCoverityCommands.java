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
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.stepworkflow.CoverityRemoteToolRunner;
import com.synopsys.integration.stepworkflow.AbstractConsumingSubStep;
import com.synopsys.integration.stepworkflow.SubStepResponse;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.remoting.VirtualChannel;

public class RunCoverityCommands extends AbstractConsumingSubStep<List<List<String>>> {
    private final JenkinsCoverityLogger logger;
    private final IntEnvironmentVariables intEnvironmentVariables;
    private final String remoteWorkingDirectory;
    private final OnCommandFailure onCommandFailure;
    private final VirtualChannel virtualChannel;

    public RunCoverityCommands(final JenkinsCoverityLogger logger, final IntEnvironmentVariables intEnvironmentVariables, final String remoteWorkingDirectory, final OnCommandFailure onCommandFailure, final VirtualChannel virtualChannel) {
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
