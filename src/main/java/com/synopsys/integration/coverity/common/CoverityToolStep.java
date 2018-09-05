/**
 * synopsys-coverity
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
package com.synopsys.integration.coverity.common;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.types.Commandline;

import com.synopsys.integration.coverity.JenkinsCoverityInstance;
import com.synopsys.integration.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.coverity.PluginHelper;
import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.config.CoverityServerConfigBuilder;
import com.synopsys.integration.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.coverity.exception.EmptyChangeSetException;
import com.synopsys.integration.coverity.executable.Executable;
import com.synopsys.integration.coverity.remote.CoverityRemoteResponse;
import com.synopsys.integration.coverity.remote.CoverityRemoteRunner;
import com.synopsys.integration.coverity.tools.CoverityToolInstallation;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.phonehome.PhoneHomeCallable;
import com.synopsys.integration.phonehome.PhoneHomeRequestBody;
import com.synopsys.integration.phonehome.PhoneHomeResponse;
import com.synopsys.integration.phonehome.PhoneHomeService;
import com.synopsys.integration.rest.RestConstants;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import jenkins.model.Jenkins;

public class CoverityToolStep extends BaseCoverityStep {
    private final List<ChangeLogSet<?>> changeLogSets;

    public CoverityToolStep(final Node node, final TaskListener listener, final EnvVars envVars, final FilePath workspace, final Run run, final List<ChangeLogSet<?>> changeLogSets) {
        super(node, listener, envVars, workspace, run);
        this.changeLogSets = changeLogSets;
    }

    private CoverityToolInstallation[] getCoverityToolInstallations() {
        return getCoverityPostBuildStepDescriptor().getCoverityToolInstallations();
    }

    public boolean runCoverityToolStep(final Optional<String> optionalCoverityToolName, final Optional<String> optionalStreamName, final Optional<Boolean> optionalContinueOnCommandFailure,
            final Optional<RepeatableCommand[]> optionalCommands, final Optional<String> optionalChangeSetNamesIncludePatterns, final Optional<String> optionalChangeSetNamesExcludePatterns) {
        final JenkinsCoverityLogger logger = createJenkinsCoverityLogger();
        try {
            final String pluginVersion = PluginHelper.getPluginVersion();
            logger.alwaysLog("Running Synopsys Coverity version : " + pluginVersion);

            if (Result.ABORTED == getResult()) {
                logger.alwaysLog("Skipping the Synopsys Coverity step because the build was aborted.");
                return false;
            }
            String streamName = "";
            if (optionalStreamName.isPresent()) {
                streamName = optionalStreamName.get();
                getEnvVars().put("COV_STREAM", streamName);
            }

            final JenkinsCoverityInstance coverityInstance = getCoverityInstance();
            logGlobalConfiguration(coverityInstance, logger);

            boolean configurationErrors = false;
            final String coverityToolName = optionalCoverityToolName.orElse("");
            final Optional<CoverityToolInstallation> optionalCoverityToolInstallation = verifyAndGetCoverityToolInstallation(coverityToolName, getCoverityToolInstallations(), getNode(), logger);
            if (!optionalCoverityToolInstallation.isPresent()) {
                setResult(Result.FAILURE);
                configurationErrors = true;
            }
            if (!verifyCoverityCommands(optionalCommands, logger)) {
                setResult(Result.FAILURE);
                configurationErrors = true;
            }
            if (configurationErrors) {
                return false;
            }

            final RepeatableCommand[] commands = optionalCommands.get();
            final Boolean continueOnCommandFailure = optionalContinueOnCommandFailure.orElse(false);
            final CoverityToolInstallation coverityToolInstallation = optionalCoverityToolInstallation.get();

            final String changeSetNamesIncludePatterns = optionalChangeSetNamesIncludePatterns.orElse("");
            final String changeSetNamesExcludePatterns = optionalChangeSetNamesExcludePatterns.orElse("");

            logger.alwaysLog("-- Synopsys Coverity Static Analysis tool: " + coverityToolInstallation.getHome());
            logger.alwaysLog("-- Synopsys stream : " + continueOnCommandFailure);
            logger.alwaysLog("-- Continue on command failure : " + continueOnCommandFailure);
            logger.alwaysLog("-- Change Set Inclusion Patterns: " + changeSetNamesIncludePatterns);
            logger.alwaysLog("-- Change Set Exclusion Patterns: " + changeSetNamesExcludePatterns);
            PhoneHomeResponse phoneHomeResponse = null;
            try {
                final URL coverityUrl = coverityInstance.getCoverityURL().orElse(null);
                if (null != coverityUrl) {
                    getEnvVars().put(Executable.COVERITY_HOST_ENVIRONMENT_VARIABLE, coverityUrl.getHost());
                    if (coverityUrl.getPort() > -1) {
                        getEnvVars().put(Executable.COVERITY_PORT_ENVIRONMENT_VARIABLE, String.valueOf(coverityUrl.getPort()));
                    }
                }

                try {
                    final CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
                    builder.url(coverityUrl.toString());
                    builder.username(coverityInstance.getCoverityUsername().orElse(null));
                    builder.password(coverityInstance.getCoverityPassword().orElse(null));

                    final CoverityServerConfig coverityServerConfig = builder.build();
                    final WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, logger, createIntEnvironmentVariables());
                    webServiceFactory.connect();

                    final ExecutorService executor = Executors.newSingleThreadExecutor();
                    try {
                        final PhoneHomeService phoneHomeService = webServiceFactory.createPhoneHomeService(executor);
                        //FIXME change to match the final artifact name
                        final PhoneHomeRequestBody.Builder phoneHomeRequestBuilder = new PhoneHomeRequestBody.Builder();
                        phoneHomeRequestBuilder.addToMetaData("jenkins.version", Jenkins.getVersion().toString());
                        final PhoneHomeCallable phoneHomeCallable = webServiceFactory.createCoverityPhoneHomeCallable(coverityUrl, "synopsys-coverity", pluginVersion, phoneHomeRequestBuilder);
                        phoneHomeResponse = phoneHomeService.startPhoneHome(phoneHomeCallable);
                    } finally {
                        executor.shutdownNow();
                    }
                } catch (final Exception e) {
                    logger.debug(e.getMessage(), e);
                }

                for (final RepeatableCommand repeatableCommand : commands) {
                    final String command = repeatableCommand.getCommand();
                    if (StringUtils.isBlank(command)) {
                        continue;
                    }
                    final String resolvedCommand;
                    try {
                        resolvedCommand = updateCommandWithChangeSet(logger, command, changeSetNamesExcludePatterns, changeSetNamesIncludePatterns);
                    } catch (final EmptyChangeSetException e) {
                        if (continueOnCommandFailure) {
                            logger.error(String.format("[WARNING] Skipping command %s because the CHANGE_SET is empty", command));
                            continue;
                        } else {
                            logger.error(String.format("[WARNING] Skipping command %s and following commands because the CHANGE_SET is empty", command));
                            break;
                        }
                    }
                    final List<String> arguments = getCorrectedParameters(resolvedCommand);
                    final CoverityRemoteRunner coverityRemoteRunner = new CoverityRemoteRunner(logger, coverityInstance.getCoverityUsername().orElse(null),
                            coverityInstance.getCoverityPassword().orElse(null),
                            coverityToolInstallation.getHome(), arguments, getWorkspace().getRemote(), getEnvVars());
                    final CoverityRemoteResponse response = getNode().getChannel().call(coverityRemoteRunner);
                    boolean shouldStop = false;
                    if (response.getExitCode() != 0) {
                        logger.error("[ERROR] Coverity failed with exit code: " + response.getExitCode());
                        setResult(Result.FAILURE);
                        shouldStop = true;
                    }
                    if (null != response.getException()) {
                        final Exception exception = response.getException();
                        if (exception instanceof InterruptedException) {
                            if (null != phoneHomeResponse) {
                                phoneHomeResponse.endPhoneHome();
                            }
                            setResult(Result.ABORTED);
                            Thread.currentThread().interrupt();
                            break;
                        } else {
                            setResult(Result.UNSTABLE);
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
                if (null != phoneHomeResponse) {
                    phoneHomeResponse.endPhoneHome();
                }
                logger.error("[ERROR] Synopsys Coverity thread was interrupted.", e);
                setResult(Result.ABORTED);
                Thread.currentThread().interrupt();
                return false;
            }
        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage(), e);
            setResult(Result.UNSTABLE);
            return false;
        }
        return true;
    }

    private Optional<CoverityToolInstallation> verifyAndGetCoverityToolInstallation(final String coverityToolName, final CoverityToolInstallation[] coverityToolInstallations, final Node node, final JenkinsCoverityLogger logger)
            throws InterruptedException {
        if (StringUtils.isBlank(coverityToolName)) {
            logger.error("[ERROR] No Coverity Static Analysis tool configured for this Job.");
            return Optional.empty();
        }
        if (null == coverityToolInstallations || coverityToolInstallations.length == 0) {
            logger.error("[ERROR] No Coverity Static Analysis tools configured in Jenkins.");
            return Optional.empty();
        }
        if (StringUtils.isNotBlank(coverityToolName) && null != coverityToolInstallations && coverityToolInstallations.length > 0) {
            for (final CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
                if (coverityToolInstallation.getName().equals(coverityToolName)) {
                    try {
                        return Optional.ofNullable(coverityToolInstallation.forNode(node, logger.getJenkinsListener()));
                    } catch (final IOException e) {
                        logger.error("Problem getting the Synopsys Coverity Static Analysis tool on node " + node.getDisplayName() + ": " + e.getMessage());
                        logger.debug(null, e);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean verifyCoverityCommands(final Optional<RepeatableCommand[]> optionalCommands, final IntLogger logger) {
        if (!optionalCommands.isPresent()) {
            logger.error("[ERROR] There are no Coverity commands configured to run.");
            return false;
        }
        final RepeatableCommand[] commands = optionalCommands.get();
        if (commands.length == 0) {
            logger.error("[ERROR] There are no Coverity commands configured to run.");
            return false;
        }
        boolean allCommandsEmpty = true;
        for (final RepeatableCommand repeatableCommand : commands) {
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

    public List<ChangeLogSet<?>> getChangeLogSets() {
        return changeLogSets;
    }

    private String getChangeSetFilePaths(final IntLogger logger, final String changeSetNamesExcludePatterns, final String changeSetNamesIncludePatterns) {
        final ChangeSetFilter changeSetFilter = new ChangeSetFilter(changeSetNamesExcludePatterns, changeSetNamesIncludePatterns);
        final List<String> filePaths = new ArrayList<>();
        if (!getChangeLogSets().isEmpty()) {
            for (final ChangeLogSet<?> changeLogSet : getChangeLogSets()) {
                if (!changeLogSet.isEmptySet()) {
                    final Object[] changeEntryObjects = changeLogSet.getItems();
                    for (final Object changeEntryObject : changeEntryObjects) {
                        final ChangeLogSet.Entry changeEntry = (ChangeLogSet.Entry) changeEntryObject;

                        final Date date = new Date(changeEntry.getTimestamp());
                        final SimpleDateFormat sdf = new SimpleDateFormat(RestConstants.JSON_DATE_FORMAT);
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                        logger.debug(String.format("Commit %s by %s on %s: %s", changeEntry.getCommitId(), changeEntry.getAuthor(), sdf.format(date), changeEntry.getMsg()));
                        final List<ChangeLogSet.AffectedFile> affectedFiles = new ArrayList<>(changeEntry.getAffectedFiles());
                        for (final ChangeLogSet.AffectedFile affectedFile : affectedFiles) {
                            final String filePath = affectedFile.getPath();
                            if (changeSetFilter.shouldInclude(filePath)) {
                                logger.debug(String.format("Type: %s File Path: %s Included in change set", affectedFile.getEditType().getName(), filePath));
                                filePaths.add(filePath);
                            } else {
                                logger.debug(String.format("Type: %s File Path: %s Excluded from change set", affectedFile.getEditType().getName(), filePath));
                            }
                        }
                    }
                }
            }
        }
        return StringUtils.join(filePaths, " ");
    }

    private String updateCommandWithChangeSet(final IntLogger logger, final String command, final String changeSetNamesExcludePatterns, final String changeSetNamesIncludePatterns) throws EmptyChangeSetException {
        String resolvedChangeSetCommand = command;
        String variable = "";
        if (command.contains("$CHANGE_SET") || command.contains("${CHANGE_SET}")) {
            if (command.contains("$CHANGE_SET")) {
                variable = "$CHANGE_SET";
            }
            if (command.contains("${CHANGE_SET}")) {
                variable = "${CHANGE_SET}";
            }
            final String filePaths = getChangeSetFilePaths(logger, changeSetNamesExcludePatterns, changeSetNamesIncludePatterns);
            if (StringUtils.isBlank(filePaths)) {
                throw new EmptyChangeSetException();
            }
            resolvedChangeSetCommand = command.replace(variable, filePaths);
        }
        return resolvedChangeSetCommand;
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
