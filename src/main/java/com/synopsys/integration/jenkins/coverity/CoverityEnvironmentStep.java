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
package com.synopsys.integration.jenkins.coverity;

import java.io.IOException;
import java.net.URL;
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
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityToolInstallation;
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
    private final List<ChangeLogSet<?>> changeLogSets;

    public CoverityEnvironmentStep(final String coverityInstanceUrl, final Node node, final TaskListener listener, final EnvVars envVars, final FilePath workspace, final Run run, final List<ChangeLogSet<?>> changeLogSets) {
        super(coverityInstanceUrl, node, listener, envVars, workspace, run);
        this.changeLogSets = changeLogSets;
    }

    public boolean setUpCoverityEnvironment(final String streamName, final String coverityToolName, final ConfigureChangeSetPatterns configureChangeSetPatterns) {
        this.initializeJenkinsCoverityLogger();
        final String pluginVersion = PluginHelper.getPluginVersion();
        logger.alwaysLog("Running Synopsys Coverity version: " + pluginVersion);

        if (Result.ABORTED == getResult()) {
            logger.alwaysLog("Skipping the Synopsys Coverity step because the build was aborted.");
            return false;
        }

        final CoverityConnectInstance coverityInstance = verifyAndGetCoverityInstance().orElse(null);
        if (coverityInstance == null) {
            logger.error("Skipping the Synopsys Coverity step because no configured Coverity server was detected in the Jenkins System Configuration.");
            return false;
        }

        logGlobalConfiguration(coverityInstance);

        final Optional<CoverityToolInstallation> optionalCoverityToolInstallation = verifyAndGetCoverityToolInstallation(StringUtils.trimToEmpty(coverityToolName), getCoverityToolInstallations(), getNode());
        if (!optionalCoverityToolInstallation.isPresent()) {
            setResult(Result.FAILURE);
            logger.error("No Coverity tool installation was configured");
            return false;
        }

        final CoverityToolInstallation coverityToolInstallation = optionalCoverityToolInstallation.get();

        setEnvironmentVariable(CoverityToolEnvironmentVariable.USER, coverityInstance.getCoverityUsername().orElse(StringUtils.EMPTY));
        setEnvironmentVariable(CoverityToolEnvironmentVariable.PASSPHRASE, coverityInstance.getCoverityPassword().orElse(StringUtils.EMPTY));
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_HOST, computeHost(coverityInstance));
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_PORT, computePort(coverityInstance));
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_STREAM, streamName);
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.CHANGE_SET, computeChangeSet(configureChangeSetPatterns));
        setEnvironmentVariable(JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME, coverityToolInstallation.getHome());

        logger.alwaysLog("-- Synopsys Coverity Static Analysis tool: " + coverityToolInstallation.getHome());
        logger.alwaysLog("-- Synopsys stream: " + streamName);
        if (configureChangeSetPatterns != null) {
            logger.alwaysLog("-- Change Set Inclusion Patterns: " + configureChangeSetPatterns.getChangeSetInclusionPatterns());
            logger.alwaysLog("-- Change Set Exclusion Patterns: " + configureChangeSetPatterns.getChangeSetExclusionPatterns());
        } else {
            logger.alwaysLog("-- No Change Set inclusion or exclusion patterns set");
        }

        final PhoneHomeResponse phoneHomeResponse = phoneHome(coverityInstance, pluginVersion);
        if (null != phoneHomeResponse) {
            phoneHomeResponse.endPhoneHome();
        }

        return true;
    }

    private Optional<CoverityToolInstallation> verifyAndGetCoverityToolInstallation(final String coverityToolName, final CoverityToolInstallation[] coverityToolInstallations, final Node node) {
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
                    } catch (final IOException | InterruptedException e) {
                        logger.error("Problem getting the Synopsys Coverity Static Analysis tool on node " + node.getDisplayName() + ": " + e.getMessage());
                        logger.debug(null, e);
                    }
                }
            }
        }

        return Optional.ofNullable(thisNodeCoverityToolInstallation);
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

    private String computeChangeSet(final ConfigureChangeSetPatterns configureChangeSetPatterns) {
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

    private CoverityToolInstallation[] getCoverityToolInstallations() {
        return getCoverityGlobalConfig().getCoverityToolInstallations();
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
