/**
 * synopsys-coverity
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
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
package com.synopsys.integration.jenkins.coverity.steps;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.executable.CoverityToolEnvironmentVariable;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.coverity.ChangeSetFilter;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.steps.remote.CoverityRemoteInstallationValidator;
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

public class CoverityEnvironmentStep extends BaseCoverityStep {
    public CoverityEnvironmentStep(final Node node, final TaskListener listener, final EnvVars envVars, final FilePath workspace, final Run run) {
        super(node, listener, envVars, workspace, run);
    }

    public boolean setUpCoverityEnvironment(final List<ChangeLogSet<?>> changeLogSets, final String coverityInstanceUrl, final String projectName, final String streamName, final String viewName,
        final ConfigureChangeSetPatterns configureChangeSetPatterns) throws IOException {
        this.initializeJenkinsCoverityLogger();
        final String pluginVersion = GlobalValueHelper.getPluginVersion();
        logger.alwaysLog("Running Synopsys Coverity version: " + pluginVersion);

        if (Result.ABORTED == getResult()) {
            logger.alwaysLog("Skipping injecting Synopsys Coverity environment because the build was aborted.");
            return false;
        }

        final CoverityConnectInstance coverityInstance = GlobalValueHelper.getCoverityInstanceWithUrl(logger, coverityInstanceUrl).orElse(null);
        if (coverityInstance == null) {
            logger.error("No Coverity Connect instance with the URL " + coverityInstanceUrl + " could be found in the Jenkins System config.");
            logger.error("Skipping Synopsys Coverity environment step...");
            return false;
        }

        logGlobalConfiguration(coverityInstance);
        final CoverityRemoteInstallationValidator coverityRemoteInstallationValidator = new CoverityRemoteInstallationValidator(logger, getEnvVars());
        final String pathToCoverityToolHome;
        try {
            pathToCoverityToolHome = getNode().getChannel().call(coverityRemoteInstallationValidator);
        } catch (final InterruptedException e) {
            logger.error("[ERROR] Synopsys Coverity thread was interrupted.");
            logger.debug("", e);
            setResult(Result.ABORTED);
            Thread.currentThread().interrupt();
            return false;
        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage());
            logger.debug("", e);
            setResult(Result.UNSTABLE);
            return false;
        }

        if (StringUtils.isBlank(pathToCoverityToolHome)) {
            logger.error("Could not get path to Coverity tool home or the path provided is invalid.");
            logger.error("Skipping Synopsys Coverity environment step...");
            return false;
        }

        addCoverityToolBinToPath(pathToCoverityToolHome);
        setEnvironmentVariable(CoverityToolEnvironmentVariable.USER, coverityInstance.getCoverityUsername().orElse(StringUtils.EMPTY));
        setEnvironmentVariable(CoverityToolEnvironmentVariable.PASSPHRASE, coverityInstance.getCoverityPassword().orElse(StringUtils.EMPTY));
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_HOST, computeHost(coverityInstance));
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_PORT, computePort(coverityInstance));
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_PROJECT, projectName);
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_STREAM, streamName);
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_VIEW, viewName);
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.CHANGE_SET, computeChangeSet(changeLogSets, configureChangeSetPatterns));
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_INTERMEDIATE_DIRECTORY, computeIntermediateDirectory(getEnvVars()));

        logger.alwaysLog("Synopsys Coverity Environment:");
        logger.alwaysLog("-- Synopsys Coverity static analysis tool home: " + pathToCoverityToolHome);
        logger.alwaysLog("-- Synopsys Coverity static analysis intermediate directory: " + getEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_INTERMEDIATE_DIRECTORY));
        logger.alwaysLog("-- Synopsys stream: " + streamName);
        if (configureChangeSetPatterns != null) {
            logger.alwaysLog("-- Change set inclusion patterns: " + configureChangeSetPatterns.getChangeSetInclusionPatterns());
            logger.alwaysLog("-- Change set exclusion patterns: " + configureChangeSetPatterns.getChangeSetExclusionPatterns());
        } else {
            logger.alwaysLog("-- No change set inclusion or exclusion patterns set");
        }

        final PhoneHomeResponse phoneHomeResponse = phoneHome(coverityInstance, pluginVersion);
        if (null != phoneHomeResponse) {
            phoneHomeResponse.endPhoneHome();
        }

        return true;
    }

    private String computeIntermediateDirectory(final EnvVars envVars) {
        final String workspace = envVars.get("WORKSPACE");
        final Path workspacePath = Paths.get(workspace);
        final Path intermediateDirectoryPath = workspacePath.resolve("idir");
        return intermediateDirectoryPath.toString();
    }

    private String computeHost(final CoverityConnectInstance coverityConnectInstance) {
        return coverityConnectInstance.getCoverityURL()
                   .map(URL::getHost)
                   .orElse(StringUtils.EMPTY);
    }

    private String computePort(final CoverityConnectInstance coverityConnectInstance) {
        final URL coverityUrl = coverityConnectInstance.getCoverityURL().orElse(null);
        final String computedPort;

        if (null != coverityUrl) {
            if (coverityUrl.getPort() == -1) {
                // If the user passes a URL that has no port, we must supply the implicit URL port. Coverity uses tomcat defaults if it can't find any ports (8080/8443)
                computedPort = String.valueOf(coverityUrl.getDefaultPort());
            } else {
                computedPort = String.valueOf(coverityUrl.getPort());
            }
        } else {
            computedPort = StringUtils.EMPTY;
        }

        return computedPort;
    }

    private String computeChangeSet(final List<ChangeLogSet<?>> changeLogSets, final ConfigureChangeSetPatterns configureChangeSetPatterns) {
        if (configureChangeSetPatterns == null) {
            return StringUtils.EMPTY;
        }

        final ChangeSetFilter changeSetFilter = new ChangeSetFilter(configureChangeSetPatterns);

        return changeLogSets.stream()
                   .filter(changeLogSet -> !changeLogSet.isEmptySet())
                   .flatMap(this::toEntries)
                   .flatMap(this::toAffectedFiles)
                   .filter(affectedFile -> shouldFilePathBeIncluded(affectedFile, changeSetFilter))
                   .map(ChangeLogSet.AffectedFile::getPath)
                   .collect(Collectors.joining(" "));
    }

    private Stream<ChangeLogSet.Entry> toEntries(final ChangeLogSet changeLogSet) {
        return Arrays.stream((ChangeLogSet.Entry[]) changeLogSet.getItems());
    }

    private Stream<? extends ChangeLogSet.AffectedFile> toAffectedFiles(final ChangeLogSet.Entry entry) {
        logEntry(entry);
        return entry.getAffectedFiles().stream();
    }

    private void logEntry(final ChangeLogSet.Entry entry) {
        if (logger.getLogLevel().isLoggable(LogLevel.DEBUG)) {
            final Date date = new Date(entry.getTimestamp());
            logger.debug(String.format("Commit %s by %s on %s: %s", entry.getCommitId(), entry.getAuthor(), RestConstants.formatDate(date), entry.getMsg()));
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

    private PhoneHomeResponse phoneHome(final CoverityConnectInstance coverityInstance, final String pluginVersion) {
        PhoneHomeResponse phoneHomeResponse = null;

        try {
            final Optional<URL> coverityUrl = coverityInstance.getCoverityURL();
            final CoverityServerConfig coverityServerConfig = coverityInstance.getCoverityServerConfig();
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
