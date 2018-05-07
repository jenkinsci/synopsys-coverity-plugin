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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.types.Commandline;

import com.blackducksoftware.integration.log.IntLogger;
import com.blackducksoftware.integration.util.CIEnvironmentVariables;
import com.sig.integration.coverity.CoverityInstance;
import com.sig.integration.coverity.JenkinsCoverityLogger;
import com.sig.integration.coverity.JenkinsProxyHelper;
import com.sig.integration.coverity.PluginHelper;
import com.sig.integration.coverity.exception.CoverityJenkinsException;
import com.sig.integration.coverity.post.CoverityPostBuildStepDescriptor;
import com.sig.integration.coverity.remote.CoverityRemoteResponse;
import com.sig.integration.coverity.remote.CoverityRemoteRunner;
import com.sig.integration.coverity.tools.CoverityToolInstallation;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

public class CoverityCommonStep {
    private final Node node;
    private final TaskListener listener;
    private final EnvVars envVars;
    private final FilePath workspace;
    private final Run run;
    private final String coverityToolName;
    private final Boolean continueOnCommandFailure;
    private final RepeatableCommand[] commands;

    public CoverityCommonStep(Node node, TaskListener listener, EnvVars envVars, FilePath workspace, Run run, String coverityToolName, Boolean continueOnCommandFailure,
            RepeatableCommand[] commands) {
        this.node = node;
        this.listener = listener;
        this.envVars = envVars;
        this.workspace = workspace;
        this.run = run;
        this.coverityToolName = coverityToolName;
        this.continueOnCommandFailure = continueOnCommandFailure;
        this.commands = commands;
    }

    private CoverityPostBuildStepDescriptor getCoverityPostBuildStepDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(CoverityPostBuildStepDescriptor.class);
    }

    private CoverityInstance getCoverityInstance() {
        return getCoverityPostBuildStepDescriptor().getCoverityInstance();
    }

    private CoverityToolInstallation[] getCoverityToolInstallations() {
        return getCoverityPostBuildStepDescriptor().getCoverityToolInstallations();
    }

    public void runCommonDetectStep() {
        final JenkinsCoverityLogger logger = new JenkinsCoverityLogger(listener);
        final CIEnvironmentVariables variables = new CIEnvironmentVariables();
        variables.putAll(envVars);
        logger.setLogLevel(variables);
        try {
            final String pluginVersion = PluginHelper.getPluginVersion();
            logger.alwaysLog("Running SIG Coverity version : " + pluginVersion);

            CoverityInstance coverityInstance = getCoverityInstance();
            logGlobalConfiguration(coverityInstance, logger);

            boolean configurationErrors = false;

            Optional<CoverityToolInstallation> optionalCoverityToolInstallation = verifyAndGetCoverityToolInstallation(coverityToolName, getCoverityToolInstallations(), logger);
            if (!optionalCoverityToolInstallation.isPresent()) {
                run.setResult(Result.FAILURE);
                configurationErrors = true;
            }
            if (!verifyCoverityCommands(commands, logger)) {
                run.setResult(Result.FAILURE);
                configurationErrors = true;
            }
            if (configurationErrors) {
                return;
            }

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
                            coverityToolInstallation.getHome(), arguments, workspace.getRemote(), envVars);
                    final CoverityRemoteResponse response = node.getChannel().call(coverityRemoteRunner);
                    boolean shouldStop = false;
                    if (response.getExitCode() != 0) {
                        logger.error("[ERROR] Coverity failed with exit code: " + response.getExitCode());
                        run.setResult(Result.FAILURE);
                        shouldStop = true;
                    }
                    if (null != response.getException()) {
                        final Exception exception = response.getException();
                        if (exception instanceof InterruptedException) {
                            run.setResult(Result.ABORTED);
                            Thread.currentThread().interrupt();
                            break;
                        } else {
                            run.setResult(Result.UNSTABLE);
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
                run.setResult(Result.ABORTED);
                Thread.currentThread().interrupt();
            }
        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage(), e);
            run.setResult(Result.UNSTABLE);
        }
    }

    private void logGlobalConfiguration(CoverityInstance coverityInstance, IntLogger logger) {
        if (null == coverityInstance) {
            logger.warn("No global Coverity configuration found.");
        } else {
            Optional<URL> optionalCoverityURL = coverityInstance.getCoverityURL();
            if (!optionalCoverityURL.isPresent()) {
                logger.warn("No Coverity URL configured.");
            } else {
                logger.alwaysLog("-- Coverity URL : " + optionalCoverityURL.get().toString());
            }
            Optional<String> optionalCoverityUsername = coverityInstance.getCoverityUsername();
            if (!optionalCoverityUsername.isPresent()) {
                logger.warn("No Coverity Username configured.");
            } else {
                logger.alwaysLog("-- Coverity username : " + optionalCoverityUsername.get());
            }
        }
    }

    private Optional<CoverityToolInstallation> verifyAndGetCoverityToolInstallation(String coverityToolName, CoverityToolInstallation[] coverityToolInstallations, IntLogger logger) {
        if (StringUtils.isBlank(coverityToolName)) {
            logger.error("[ERROR] No Coverity Static Analysis tool configured for this Job.");
        }
        if (null == coverityToolInstallations || coverityToolInstallations.length == 0) {
            logger.error("[ERROR] No Coverity Static Analysis tools configured in Jenkins.");
        }
        if (StringUtils.isNotBlank(coverityToolName) && null != coverityToolInstallations && coverityToolInstallations.length > 0) {
            for (CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
                if (coverityToolInstallation.getName().equals(coverityToolName)) {
                    return Optional.of(coverityToolInstallation);
                }
            }
        }
        return Optional.empty();
    }

    private boolean verifyCoverityCommands(RepeatableCommand[] commands, IntLogger logger) {
        if (null == commands || commands.length == 0) {
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

    public List<String> getCorrectedParameters(final String command) throws CoverityJenkinsException {
        final String[] separatedParameters = Commandline.translateCommandline(command);
        final List<String> correctedParameters = new ArrayList<>();
        for (final String parameter : separatedParameters) {
            correctedParameters.add(handleVariableReplacement(envVars, parameter));
        }
        return correctedParameters;
    }

    public String handleVariableReplacement(final Map<String, String> variables, final String value) throws CoverityJenkinsException {
        if (value != null) {
            final String newValue = Util.replaceMacro(value, variables);
            if (newValue.contains("$")) {
                throw new CoverityJenkinsException("Variable was not properly replaced. Value : " + value + ", Result : " + newValue + ". Make sure the variable has been properly defined.");
            }
            return newValue;
        }
        return null;
    }

    public JenkinsProxyHelper getJenkinsProxyHelper() {
        return new JenkinsProxyHelper();
    }
}
