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
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.types.Commandline;

import com.synopsys.integration.coverity.JenkinsCoverityInstance;
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
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.LogLevel;
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
    public static final String JENKINS_CHANGE_SET = "${CHANGE_SET}";
    public static final String JENKINS_CHANGE_SET_BRACKETLESS = "$CHANGE_SET";
    public static final String COVERITY_STREAM_ENVIRONMENT_VARIABLE = "COV_STREAM";
    private final List<ChangeLogSet<?>> changeLogSets;

    public CoverityToolStep(final Node node, final TaskListener listener, final EnvVars envVars, final FilePath workspace, final Run run, final List<ChangeLogSet<?>> changeLogSets) {
        super(node, listener, envVars, workspace, run);
        this.changeLogSets = changeLogSets;
    }

    public RepeatableCommand[] getSimpleModeCommands(final String buildCommand, final CoverityAnalysisType coverityAnalysisType) {
        final RepeatableCommand[] commands;
        final boolean isHttps = getCoverityInstance()
                                    .flatMap(JenkinsCoverityInstance::getCoverityURL)
                                    .map(URL::getProtocol)
                                    .filter("https"::equals)
                                    .isPresent();

        if (CoverityAnalysisType.COV_ANALYZE.equals(coverityAnalysisType)) {
            commands = new RepeatableCommand[] { RepeatableCommand.DEFAULT_COV_BUILD(buildCommand), RepeatableCommand.DEFAULT_COV_ANALYZE(), RepeatableCommand.DEFAULT_COV_COMMIT_DEFECTS(isHttps) };
        } else if (CoverityAnalysisType.COV_RUN_DESKTOP.equals(coverityAnalysisType)) {
            commands = new RepeatableCommand[] { RepeatableCommand.DEFAULT_COV_BUILD(buildCommand), RepeatableCommand.DEFAULT_COV_RUN_DESKTOP(isHttps, JENKINS_CHANGE_SET), RepeatableCommand.DEFAULT_COV_COMMIT_DEFECTS(isHttps) };
        } else {
            commands = new RepeatableCommand[] {};
        }

        return commands;
    }

    public boolean runCoverityToolStep(final String coverityToolName, final String streamName, final RepeatableCommand[] commands, final OnCommandFailure onCommandFailure, final boolean changeSetPatternsConfigured,
        final String changeSetNamesIncludePatterns, final String changeSetNamesExcludePatterns) {
        initializeJenkinsCoverityLogger();
        try {
            final String pluginVersion = PluginHelper.getPluginVersion();
            logger.alwaysLog("Running Synopsys Coverity version: " + pluginVersion);

            if (Result.ABORTED == getResult()) {
                logger.alwaysLog("Skipping the Synopsys Coverity step because the build was aborted.");
                return false;
            }
            if (StringUtils.isNotBlank(streamName)) {
                getEnvVars().put(COVERITY_STREAM_ENVIRONMENT_VARIABLE, streamName);
            }

            final JenkinsCoverityInstance coverityInstance = getCoverityInstance().orElse(null);
            if (coverityInstance == null) {
                logger.error("Skipping the Synopsys Coverity step because no configured Coverity server was detected in the Jenkins System Configuration.");
                return false;
            }

            logGlobalConfiguration(getCoverityInstance().orElse(null));

            boolean configurationErrors = false;
            final Optional<CoverityToolInstallation> optionalCoverityToolInstallation = verifyAndGetCoverityToolInstallation(StringUtils.trimToEmpty(coverityToolName), getCoverityToolInstallations(), getNode());
            if (!optionalCoverityToolInstallation.isPresent()) {
                setResult(Result.FAILURE);
                configurationErrors = true;
            }

            if (!verifyCoverityCommands(commands)) {
                setResult(Result.FAILURE);
                configurationErrors = true;
            }

            if (configurationErrors) {
                return false;
            }

            final CoverityToolInstallation coverityToolInstallation = optionalCoverityToolInstallation.get();

            logger.alwaysLog("-- Synopsys Coverity Static Analysis tool: " + coverityToolInstallation.getHome());
            logger.alwaysLog("-- Synopsys stream: " + streamName);
            logger.alwaysLog("-- On command failure: " + onCommandFailure);
            if (changeSetPatternsConfigured) {
                logger.alwaysLog("-- Change Set Inclusion Patterns: " + changeSetNamesIncludePatterns);
                logger.alwaysLog("-- Change Set Exclusion Patterns: " + changeSetNamesExcludePatterns);
            } else {
                logger.alwaysLog("-- No Change Set inclusion or exclusion patterns set");
            }
            PhoneHomeResponse phoneHomeResponse = null;
            try {
                final URL coverityUrl = coverityInstance.getCoverityURL().orElse(null);
                if (null != coverityUrl) {
                    getEnvVars().put(Executable.COVERITY_HOST_ENVIRONMENT_VARIABLE, coverityUrl.getHost());
                    if (coverityUrl.getPort() > -1) {
                        getEnvVars().put(Executable.COVERITY_PORT_ENVIRONMENT_VARIABLE, String.valueOf(coverityUrl.getPort()));
                    }
                }

                phoneHomeResponse = phoneHome(coverityInstance, pluginVersion);

                executeCoverityCommands(commands, changeSetPatternsConfigured, changeSetNamesExcludePatterns, changeSetNamesIncludePatterns, onCommandFailure, coverityInstance, coverityToolInstallation, phoneHomeResponse);

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

    private void executeCoverityCommands(final RepeatableCommand[] commands, final boolean changeSetPatternsConfigured, final String changeSetNamesExcludePatterns, final String changeSetNamesIncludePatterns,
        final OnCommandFailure onCommandFailure, final JenkinsCoverityInstance coverityInstance, final CoverityToolInstallation coverityToolInstallation, final PhoneHomeResponse phoneHomeResponse)
        throws IntegrationException, InterruptedException, IOException {
        for (final RepeatableCommand repeatableCommand : commands) {

            final String command = repeatableCommand.getCommand();
            if (StringUtils.isBlank(command)) {
                continue;
            }
            final String resolvedCommand;
            if (changeSetPatternsConfigured) {
                try {
                    resolvedCommand = updateCommandWithChangeSet(command, changeSetNamesExcludePatterns, changeSetNamesIncludePatterns);
                } catch (final EmptyChangeSetException e) {
                    if (OnCommandFailure.EXECUTE_REMAINING_COMMANDS.equals(onCommandFailure)) {
                        logger.error(String.format("[WARNING] Skipping command %s because the CHANGE_SET is empty", command));
                        continue;
                    } else {
                        logger.error(String.format("[WARNING] Skipping command %s and following commands because the CHANGE_SET is empty", command));
                        break;
                    }
                }
            } else {
                resolvedCommand = command;
            }
            final List<String> arguments = getCorrectedParameters(resolvedCommand);
            final CoverityRemoteRunner coverityRemoteRunner = new CoverityRemoteRunner(logger, coverityInstance.getCoverityUsername().orElse(null), coverityInstance.getCoverityPassword().orElse(null),
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
            if (OnCommandFailure.SKIP_REMAINING_COMMANDS.equals(onCommandFailure) && shouldStop) {
                break;
            }
        }

    }

    private Optional<CoverityToolInstallation> verifyAndGetCoverityToolInstallation(final String coverityToolName, final CoverityToolInstallation[] coverityToolInstallations, final Node node) throws InterruptedException {
        CoverityToolInstallation thisNodeCoverityToolInstallation = null;

        if (StringUtils.isBlank(coverityToolName)) {
            logger.error("[ERROR] No Coverity Static Analysis tool configured for this Job.");
        } else if (null == coverityToolInstallations || coverityToolInstallations.length == 0) {
            logger.error("[ERROR] No Coverity Static Analysis tools configured in Jenkins.");
        } else {
            for (final CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
                if (coverityToolInstallation.getName().equals(coverityToolName)) {
                    try {
                        thisNodeCoverityToolInstallation = coverityToolInstallation.forNode(node, logger.getJenkinsListener());
                    } catch (final IOException e) {
                        logger.error("Problem getting the Synopsys Coverity Static Analysis tool on node " + node.getDisplayName() + ": " + e.getMessage());
                        logger.debug(null, e);
                    }
                }
            }
        }

        return Optional.ofNullable(thisNodeCoverityToolInstallation);
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

    private CoverityToolInstallation[] getCoverityToolInstallations() {
        return getCoverityGlobalConfig().getCoverityToolInstallations();
    }

    private String updateCommandWithChangeSet(final String command, final String changeSetNamesExcludePatterns, final String changeSetNamesIncludePatterns) throws EmptyChangeSetException {
        String resolvedChangeSetCommand = command;

        if (command.contains(JENKINS_CHANGE_SET_BRACKETLESS) || command.contains(JENKINS_CHANGE_SET)) {
            final ChangeSetFilter changeSetFilter = new ChangeSetFilter(changeSetNamesExcludePatterns, changeSetNamesIncludePatterns);

            final Stream<ChangeLogSet.Entry> changeSetEntryStream = changeLogSets.stream()
                                                                        .filter(changeLogSet -> !changeLogSet.isEmptySet())
                                                                        .map(ChangeLogSet::getItems)
                                                                        .flatMap(Arrays::stream)
                                                                        .map(changeEntryObject -> (ChangeLogSet.Entry) changeEntryObject);

            final String filePaths = changeSetEntryStream
                                         .peek(this::logChangeSetEntry)
                                         .map(ChangeLogSet.Entry::getAffectedFiles)
                                         .flatMap(Collection::stream)
                                         .filter(affectedFile -> shouldFilePathBeIncluded(affectedFile, changeSetFilter))
                                         .map(ChangeLogSet.AffectedFile::getPath)
                                         .collect(Collectors.joining(" "));

            if (StringUtils.isBlank(filePaths)) {
                throw new EmptyChangeSetException();
            }

            resolvedChangeSetCommand = command.replaceAll(Pattern.quote(JENKINS_CHANGE_SET), filePaths).replaceAll(Pattern.quote(JENKINS_CHANGE_SET_BRACKETLESS), filePaths);
        }

        return resolvedChangeSetCommand;
    }

    private void logChangeSetEntry(final ChangeLogSet.Entry changeEntry) {
        if (logger.getLogLevel().isLoggable(LogLevel.DEBUG)) {
            final Date date = new Date(changeEntry.getTimestamp());
            final SimpleDateFormat sdf = new SimpleDateFormat(RestConstants.JSON_DATE_FORMAT);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            logger.debug(String.format("Commit %s by %s on %s: %s", changeEntry.getCommitId(), changeEntry.getAuthor(), sdf.format(date), changeEntry.getMsg()));
        }
    }

    private boolean shouldFilePathBeIncluded(final ChangeLogSet.AffectedFile affectedFile, final ChangeSetFilter changeSetFilter) {
        final String affectedFilePath = affectedFile.getPath();
        final String affectedEditType = affectedFile.getEditType().getName();
        final boolean shouldInclude = changeSetFilter.shouldInclude(affectedFilePath);

        if (shouldInclude) {
            logger.debug(String.format("Type: %s File Path: %s Included in change set", affectedEditType, affectedFilePath));
        } else {
            logger.debug(String.format("Type: %s File Path: %s Excluded from change set", affectedEditType, affectedFilePath));
        }

        return shouldInclude;
    }

    private List<String> getCorrectedParameters(final String command) throws CoverityJenkinsException {
        final String[] separatedParameters = Commandline.translateCommandline(command);
        final List<String> correctedParameters = new ArrayList<>();
        for (final String parameter : separatedParameters) {
            correctedParameters.add(handleVariableReplacement(getEnvVars(), parameter));
        }

        return correctedParameters;
    }

    private PhoneHomeResponse phoneHome(final JenkinsCoverityInstance coverityInstance, final String pluginVersion) {
        PhoneHomeResponse phoneHomeResponse = null;

        try {
            final CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
            final Optional<URL> coverityUrl = coverityInstance.getCoverityURL();

            builder.url(coverityUrl.map(URL::toString).orElse(null));
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
                final PhoneHomeCallable phoneHomeCallable = webServiceFactory.createCoverityPhoneHomeCallable(coverityUrl.orElse(null), "synopsys-coverity", pluginVersion, phoneHomeRequestBuilder);
                phoneHomeResponse = phoneHomeService.startPhoneHome(phoneHomeCallable);
            } finally {
                executor.shutdownNow();
            }
        } catch (final Exception e) {
            logger.debug(e.getMessage(), e);
        }

        return phoneHomeResponse;
    }

}
