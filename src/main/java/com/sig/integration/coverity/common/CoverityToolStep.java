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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.types.Commandline;

import com.blackducksoftware.integration.log.IntLogger;
import com.sig.integration.coverity.JenkinsCoverityInstance;
import com.sig.integration.coverity.JenkinsCoverityLogger;
import com.sig.integration.coverity.PluginHelper;
import com.sig.integration.coverity.exception.CoverityJenkinsException;
import com.sig.integration.coverity.remote.CoverityRemoteResponse;
import com.sig.integration.coverity.remote.CoverityRemoteRunner;
import com.sig.integration.coverity.tools.CoverityToolInstallation;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class CoverityToolStep extends BaseCoverityStep {

    public CoverityToolStep(Node node, TaskListener listener, EnvVars envVars, FilePath workspace, Run run) {
        super(node, listener, envVars, workspace, run);
    }

    private CoverityToolInstallation[] getCoverityToolInstallations() {
        return getCoverityPostBuildStepDescriptor().getCoverityToolInstallations();
    }

    public boolean runCoverityToolStep(Optional<String> optionalCoverityToolName, Optional<Boolean> optionalContinueOnCommandFailure, Optional<RepeatableCommand[]> optionalCommands) {
        final JenkinsCoverityLogger logger = createJenkinsCoverityLogger();
        try {
            final String pluginVersion = PluginHelper.getPluginVersion();
            logger.alwaysLog("Running SIG Coverity version : " + pluginVersion);

            JenkinsCoverityInstance coverityInstance = getCoverityInstance();
            logGlobalConfiguration(coverityInstance, logger);

            boolean configurationErrors = false;
            String coverityToolName = optionalCoverityToolName.orElse("");
            Optional<CoverityToolInstallation> optionalCoverityToolInstallation = verifyAndGetCoverityToolInstallation(coverityToolName, getCoverityToolInstallations(), getNode(), logger);
            if (!optionalCoverityToolInstallation.isPresent()) {
                getRun().setResult(Result.FAILURE);
                configurationErrors = true;
            }
            if (!verifyCoverityCommands(optionalCommands, logger)) {
                getRun().setResult(Result.FAILURE);
                configurationErrors = true;
            }
            if (configurationErrors) {
                return false;
            }
            RepeatableCommand[] commands = optionalCommands.get();
            Boolean continueOnCommandFailure = optionalContinueOnCommandFailure.orElse(false);
            CoverityToolInstallation coverityToolInstallation = optionalCoverityToolInstallation.get();
            logger.alwaysLog("-- SIG Coverity Static Analysis tool: " + coverityToolInstallation.getHome());
            try {
                for (RepeatableCommand repeatableCommand : commands) {
                    String command = repeatableCommand.getCommand();
                    if (StringUtils.isBlank(command)) {
                        continue;
                    }
                    List<String> arguments = getCorrectedParameters(command);
                    CoverityRemoteRunner coverityRemoteRunner = new CoverityRemoteRunner(logger, coverityInstance.getCoverityURL().orElse(null), coverityInstance.getCoverityUsername().orElse(null),
                            coverityInstance.getCoverityPassword().orElse(null),
                            coverityToolInstallation.getHome(), arguments, getWorkspace().getRemote(), getEnvVars());
                    final CoverityRemoteResponse response = getNode().getChannel().call(coverityRemoteRunner);
                    boolean shouldStop = false;
                    if (response.getExitCode() != 0) {
                        logger.error("[ERROR] Coverity failed with exit code: " + response.getExitCode());
                        getRun().setResult(Result.FAILURE);
                        shouldStop = true;
                    }
                    if (null != response.getException()) {
                        final Exception exception = response.getException();
                        if (exception instanceof InterruptedException) {
                            getRun().setResult(Result.ABORTED);
                            Thread.currentThread().interrupt();
                            break;
                        } else {
                            getRun().setResult(Result.UNSTABLE);
                            shouldStop = true;
                            logger.error("[ERROR] " + exception.getMessage());
                            logger.debug(null, exception);
                        }
                    }
                    if (!continueOnCommandFailure && shouldStop) {
                        break;

                    }
                }
            } catch (final InterruptedException e) {
                logger.error("[ERROR] SIG Coverity thread was interrupted.", e);
                getRun().setResult(Result.ABORTED);
                Thread.currentThread().interrupt();
                return false;
            }
        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage(), e);
            getRun().setResult(Result.UNSTABLE);
            return false;
        }
        return true;
    }

    private Optional<CoverityToolInstallation> verifyAndGetCoverityToolInstallation(String coverityToolName, CoverityToolInstallation[] coverityToolInstallations, Node node, JenkinsCoverityLogger logger) throws InterruptedException {
        if (StringUtils.isBlank(coverityToolName)) {
            logger.error("[ERROR] No Coverity Static Analysis tool configured for this Job.");
            return Optional.empty();
        }
        if (null == coverityToolInstallations || coverityToolInstallations.length == 0) {
            logger.error("[ERROR] No Coverity Static Analysis tools configured in Jenkins.");
            return Optional.empty();
        }
        if (StringUtils.isNotBlank(coverityToolName) && null != coverityToolInstallations && coverityToolInstallations.length > 0) {
            for (CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
                if (coverityToolInstallation.getName().equals(coverityToolName)) {
                    try {
                        return Optional.ofNullable(coverityToolInstallation.forNode(node, logger.getJenkinsListener()));
                    } catch (IOException e) {
                        logger.error("Problem getting the SIG Coverity Static Analysis tool on node " + node.getDisplayName() + ": " + e.getMessage());
                        logger.debug(null, e);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean verifyCoverityCommands(Optional<RepeatableCommand[]> optionalCommands, IntLogger logger) {
        if (!optionalCommands.isPresent()) {
            logger.error("[ERROR] There are no Coverity commands configured to run.");
            return false;
        }
        RepeatableCommand[] commands = optionalCommands.get();
        if (commands.length == 0) {
            logger.error("[ERROR] There are no Coverity commands configured to run.");
            return false;
        }
        boolean allCommandsEmpty = true;
        for (RepeatableCommand repeatableCommand : commands) {
            if (StringUtils.isNotBlank(repeatableCommand.getCommand())) {
                allCommandsEmpty = false;
                break;
            }
        }
        if (allCommandsEmpty) {
            logger.error("[ERROR] The are no non-empty Coverity commands configured.");
            return false;
        }
        return true;
    }

    private List<String> getCorrectedParameters(final String command) throws CoverityJenkinsException {
        final String[] separatedParameters = Commandline.translateCommandline(command);
        final List<String> correctedParameters = new ArrayList<>();
        for (final String parameter : separatedParameters) {
            correctedParameters.add(handleVariableReplacement(getEnvVars(), parameter));
        }
        return correctedParameters;
    }

}
