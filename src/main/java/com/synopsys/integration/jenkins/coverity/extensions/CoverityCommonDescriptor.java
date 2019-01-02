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
package com.synopsys.integration.jenkins.coverity.extensions;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.ws.WebServiceException;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.config.CoverityServerConfigBuilder;
import com.synopsys.integration.coverity.config.CoverityServerConfigValidator;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.v9.ProjectDataObj;
import com.synopsys.integration.coverity.ws.v9.ProjectIdDataObj;
import com.synopsys.integration.coverity.ws.v9.StreamDataObj;
import com.synopsys.integration.coverity.ws.v9.StreamIdDataObj;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.cache.BaseCacheData;
import com.synopsys.integration.jenkins.coverity.cache.ProjectCacheData;
import com.synopsys.integration.jenkins.coverity.cache.ViewCacheData;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.RepeatableCommand;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityToolInstallation;
import com.synopsys.integration.log.LogLevel;
import com.synopsys.integration.log.PrintStreamIntLogger;
import com.synopsys.integration.validator.FieldEnum;
import com.synopsys.integration.validator.ValidationResult;
import com.synopsys.integration.validator.ValidationResults;

import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;

public class CoverityCommonDescriptor {
    private static final Logger logger = Logger.getLogger(CoverityCommonDescriptor.class.getName());

    private final ProjectCacheData projectCacheData = new ProjectCacheData();
    private final ViewCacheData viewCacheData = new ViewCacheData();

    public ListBoxModel doFillCoverityInstanceUrlItems(final String selectedCoverityInstanceUrl) {
        final Optional<CoverityConnectInstance[]> jenkinsCoverityInstances = getGlobalCoverityConnectInstances();
        if (jenkinsCoverityInstances.isPresent()) {
            return transformDataObjectArrayToListBoxModel(jenkinsCoverityInstances.get(), CoverityConnectInstance::getUrl, selectedCoverityInstanceUrl);
        } else {
            return new ListBoxModel();
        }
    }

    public FormValidation doCheckCoverityInstanceUrl(final String coverityInstance) {
        final Optional<CoverityConnectInstance[]> jenkinsCoverityInstances = getGlobalCoverityConnectInstances();
        if (!jenkinsCoverityInstances.isPresent() || jenkinsCoverityInstances.get().length == 0) {
            return FormValidation.error("There are no Coverity instances configured");
        }

        if (StringUtils.isBlank(coverityInstance)) {
            return FormValidation.error("Please choose one of the Coverity instances");
        }

        final boolean hasMatchingToolName = Stream.of(jenkinsCoverityInstances.get())
                                                .map(CoverityConnectInstance::getUrl)
                                                .anyMatch(coverityInstance::equals);

        if (hasMatchingToolName) {
            return FormValidation.ok();
        }
        return FormValidation.error("There are no Coverity instances configured with the name %s", coverityInstance);
    }

    public ListBoxModel doFillCoverityToolNameItems(final String selectedCoverityInstallation) {
        final Optional<CoverityToolInstallation[]> coverityToolInstallations = getGlobalCoverityToolInstallations();
        if (coverityToolInstallations.isPresent()) {
            return transformDataObjectArrayToListBoxModel(coverityToolInstallations.get(), CoverityToolInstallation::getName, selectedCoverityInstallation);
        } else {
            return new ListBoxModel();
        }
    }

    public FormValidation doCheckCoverityToolName(final String coverityToolName) {
        final Optional<CoverityToolInstallation[]> coverityToolInstallations = getGlobalCoverityToolInstallations();
        if (!coverityToolInstallations.isPresent() || coverityToolInstallations.get().length == 0) {
            return FormValidation.error("There are no Coverity static analysis installations configured");
        }

        if (StringUtils.isBlank(coverityToolName)) {
            return FormValidation.error("Please choose one of the Coverity static analysis installations");
        }

        final boolean hasMatchingToolName = Arrays.stream(coverityToolInstallations.get())
                                                .map(CoverityToolInstallation::getName)
                                                .anyMatch(coverityToolName::equals);

        if (hasMatchingToolName) {
            return FormValidation.ok();
        }
        return FormValidation.error("There are no Coverity static analysis installations configured with the name %s", coverityToolName);
    }

    public ListBoxModel doFillBuildStatusForIssuesItems() {
        return getListBoxModelOf(BuildStatus.values());
    }

    public ListBoxModel doFillCoverityAnalysisTypeItems() {
        return getListBoxModelOf(CoverityAnalysisType.values());
    }

    public ListBoxModel doFillOnCommandFailureItems() {
        return getListBoxModelOf(OnCommandFailure.values());
    }

    public ListBoxModel doFillProjectNameItems(final String jenkinsCoverityInstanceUrl, final String selectedProjectName, final Boolean updateNow) {
        if (checkAndWaitForProjectCacheData(jenkinsCoverityInstanceUrl, updateNow)) {
            return projectCacheData.getCachedData().stream()
                       .map(ProjectDataObj::getId)
                       .filter(Objects::nonNull)
                       .map(ProjectIdDataObj::getName)
                       .filter(Objects::nonNull)
                       .map(projectName -> wrapAsListBoxModelOption(projectName, selectedProjectName))
                       .collect(Collectors.toCollection(ListBoxModel::new));
        }

        return new ListBoxModel();
    }

    public ListBoxModel doFillStreamNameItems(final String jenkinsCoverityInstanceUrl, final String selectedProjectName, final String selectedStreamName, final Boolean updateNow) {
        if (checkAndWaitForProjectCacheData(jenkinsCoverityInstanceUrl, updateNow)) {
            return projectCacheData.getCachedData().stream()
                       .filter(projectDataObj -> isMatchingProject(projectDataObj, selectedProjectName))
                       .map(ProjectDataObj::getStreams)
                       .filter(Objects::nonNull)
                       .flatMap(List::stream)
                       .map(StreamDataObj::getId)
                       .filter(Objects::nonNull)
                       .map(StreamIdDataObj::getName)
                       .filter(Objects::nonNull)
                       .filter(StringUtils::isNotBlank)
                       .map(streamName -> wrapAsListBoxModelOption(streamName, selectedStreamName))
                       .collect(Collectors.toCollection(ListBoxModel::new));
        }

        return new ListBoxModel();
    }

    public ListBoxModel doFillViewNameItems(final String jenkinsCoverityInstanceUrl, final String selectedViewName, final Boolean updateNow) {
        if (checkAndWaitForViewCacheData(jenkinsCoverityInstanceUrl, updateNow)) {
            return viewCacheData.getCachedData().stream()
                       .filter(StringUtils::isNotBlank)
                       .map(viewName -> wrapAsListBoxModelOption(viewName, selectedViewName))
                       .collect(Collectors.toCollection(ListBoxModel::new));
        }

        return new ListBoxModel();
    }

    public FormValidation testConnectionIgnoreSuccessMessage(final String jenkinsCoverityInstanceUrl) {
        final Optional<CoverityConnectInstance[]> jenkinsCoverityInstances = getGlobalCoverityConnectInstances();
        if (jenkinsCoverityInstances.isPresent()) {
            final Optional<CoverityConnectInstance> jenkinsCoverityInstance = findDataObjectThatMatchesDisplayName(jenkinsCoverityInstances.get(), CoverityConnectInstance::getUrl, jenkinsCoverityInstanceUrl);
            if (jenkinsCoverityInstance.isPresent()) {
                return testConnectionIgnoreSuccessMessage(jenkinsCoverityInstance.get());
            }
        }
        return FormValidation.error("There are no Coverity instances configured with the name %s", jenkinsCoverityInstanceUrl);
    }

    public FormValidation testConnectionIgnoreSuccessMessage(final CoverityConnectInstance coverityConnectInstance) {
        final FormValidation connectionTest = testConnectionToCoverityInstance(coverityConnectInstance);
        if (FormValidation.Kind.OK.equals(connectionTest.kind)) {
            return FormValidation.ok();
        } else {
            return connectionTest;
        }
    }

    public FormValidation testConnectionToCoverityInstance(final CoverityConnectInstance coverityConnectInstance) {
        final String url = coverityConnectInstance.getCoverityURL().map(URL::toString).orElse(null);
        final String username = coverityConnectInstance.getCoverityUsername().orElse(null);
        final String password = coverityConnectInstance.getCoverityPassword().orElse(null);

        try {
            final CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
            builder.url(url).username(username).password(password);
            final CoverityServerConfigValidator validator = builder.createValidator();
            final ValidationResults results = validator.assertValid();
            if (!results.isEmpty() && (results.hasErrors() || results.hasWarnings())) {
                // Create a nicer more readable string to show the User instead of what the builder exception will provide
                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(String.format("Could not connect to Coverity server%s", System.lineSeparator()));
                for (final Map.Entry<FieldEnum, Set<ValidationResult>> entry : results.getResultMap().entrySet()) {
                    final String fieldName = entry.getKey().name();
                    final String validationMessages = entry.getValue().stream().map(ValidationResult::getMessage).collect(Collectors.joining(", "));
                    stringBuilder.append(String.format("%s: %s%s", fieldName, validationMessages, System.lineSeparator()));
                }
                return FormValidation.error(stringBuilder.toString());
            }

            final CoverityServerConfig coverityServerConfig = builder.buildObject();
            final WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, new PrintStreamIntLogger(System.out, LogLevel.DEBUG));

            webServiceFactory.connect();

            return FormValidation.ok("Successfully connected to " + url);
        } catch (final MalformedURLException e) {
            return FormValidation.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (final WebServiceException e) {
            if (org.apache.commons.lang.StringUtils.containsIgnoreCase(e.getMessage(), "Unauthorized")) {
                return FormValidation.error(e, String.format("Web service error occurred when attempting to connect to %s%s%s: %s", url, System.lineSeparator(), e.getClass().getSimpleName(), e.getMessage()));
            }
            return FormValidation.error(e, String.format("User authentication failed when attempting to connect to %s%s%s: %s", url, System.lineSeparator(), e.getClass().getSimpleName(), e.getMessage()));
        } catch (final CoverityIntegrationException e) {
            return FormValidation.error(e, e.getMessage());
        } catch (final Exception e) {
            return FormValidation.error(e, String.format("An unexpected error occurred when attempting to connect to %s%s%s: %s", url, System.lineSeparator(), e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    public FormValidation doCheckCovBuildArguments(final String covBuildArguments) {
        return checkForAlreadyProvidedArguments(covBuildArguments, RepeatableCommand.Argument.DIR);
    }

    public FormValidation doCheckCovAnalyzeArguments(final String covAnalyzeArguments) {
        return checkForAlreadyProvidedArguments(covAnalyzeArguments, RepeatableCommand.Argument.DIR);
    }

    public FormValidation doCheckCovRunDesktopArguments(final String covRunDesktopArguments) {
        return checkForAlreadyProvidedArguments(covRunDesktopArguments, RepeatableCommand.Argument.DIR, RepeatableCommand.Argument.HOST, RepeatableCommand.Argument.PORT, RepeatableCommand.Argument.STREAM, RepeatableCommand.Argument.SSL);
    }

    public FormValidation doCheckCovCommitDefectsArguments(final String covCommitDefectsArguments) {
        return checkForAlreadyProvidedArguments(covCommitDefectsArguments, RepeatableCommand.Argument.DIR, RepeatableCommand.Argument.HOST, RepeatableCommand.Argument.PORT, RepeatableCommand.Argument.STREAM, RepeatableCommand.Argument.SSL);
    }

    private <T> ListBoxModel transformDataObjectArrayToListBoxModel(final T[] dataObjects, final Function<T, String> displayNameMapper, final String selectedDataObjectDisplayName) {
        final ListBoxModel boxModel;

        if (null == dataObjects) {
            boxModel = new ListBoxModel();
        } else {
            boxModel = Arrays.stream(dataObjects)
                           .map(displayNameMapper)
                           .map(dataObject -> wrapAsListBoxModelOption(dataObject, selectedDataObjectDisplayName))
                           .collect(ListBoxModel::new, ListBoxModel::add, ListBoxModel::addAll);
        }

        boxModel.add("- none -", "");
        return boxModel;
    }

    private <T> Optional<T> findDataObjectThatMatchesDisplayName(final T[] dataObjects, final Function<T, String> displayNameMapper, final String dataObjectDisplayNameToFind) {
        return Arrays.stream(dataObjects)
                   .filter(dataObject -> displayNameMapper.apply(dataObject).equals(dataObjectDisplayNameToFind))
                   .findFirst();
    }

    private Boolean isMatchingProject(final ProjectDataObj projectDataObj, final String selectedProjectName) {
        return null != projectDataObj
                   && null != projectDataObj.getId()
                   && null != projectDataObj.getId().getName()
                   && projectDataObj.getId().getName().equals(selectedProjectName);
    }

    private ListBoxModel.Option wrapAsListBoxModelOption(final String nameValue, final String selectedNameValue) {
        return new ListBoxModel.Option(nameValue, nameValue, nameValue.equals(selectedNameValue));
    }

    private FormValidation checkForAlreadyProvidedArguments(final String command, final RepeatableCommand.Argument... providedArguments) {
        final String alreadyProvidedArguments = Arrays.stream(providedArguments)
                                                    .map(RepeatableCommand.Argument::toString)
                                                    .filter(command::contains)
                                                    .collect(Collectors.joining(", "));

        if (StringUtils.isNotBlank(alreadyProvidedArguments)) {
            return FormValidation.error(String.format("The argument(s) %s are automatically provided in this mode. If you wish to override, configure the 'Run custom Coverity commands' section instead.", alreadyProvidedArguments));
        }
        return FormValidation.ok();
    }

    private ListBoxModel getListBoxModelOf(final CoveritySelectBoxEnum[] coveritySelectBoxEnumValues) {
        return Stream.of(coveritySelectBoxEnumValues)
                   .collect(ListBoxModel::new, (model, value) -> model.add(value.getDisplayName(), value.name()), ListBoxModel::addAll);
    }

    private boolean checkAndWaitForProjectCacheData(final String jenkinsCoverityInstanceUrl, final Boolean updateNow) {
        return checkAndWaitForCacheData(jenkinsCoverityInstanceUrl, updateNow, projectCacheData);
    }

    private boolean checkAndWaitForViewCacheData(final String jenkinsCoverityInstanceUrl, final Boolean updateNow) {
        return checkAndWaitForCacheData(jenkinsCoverityInstanceUrl, updateNow, viewCacheData);
    }

    private boolean checkAndWaitForCacheData(final String jenkinsCoverityInstanceUrl, final Boolean updateNow, final BaseCacheData cacheData) {
        final Optional<CoverityConnectInstance[]> jenkinsCoverityInstances = getGlobalCoverityConnectInstances();
        if (jenkinsCoverityInstances.isPresent()) {
            final Optional<CoverityConnectInstance> jenkinsCoverityInstanceOptional = Stream.of(jenkinsCoverityInstances.get())
                                                                                          .filter(jenkinsCoverityInstance -> jenkinsCoverityInstance.getUrl().equals(jenkinsCoverityInstanceUrl))
                                                                                          .findAny();
            if (jenkinsCoverityInstanceOptional.isPresent()) {
                try {
                    cacheData.checkAndWaitForData(jenkinsCoverityInstanceOptional.get(), updateNow);
                    return true;
                } catch (final IntegrationException | InterruptedException e) {
                    logger.log(Level.WARNING, "Unexpected exception encountered when checking or waiting for Coverity data", e);
                } catch (final IllegalStateException ignored) {
                    // Handled by form validation
                }
            }
        }
        return false;
    }

    private CoverityGlobalConfig getCoverityGlobalConfig() {
        return GlobalConfiguration.all().get(CoverityGlobalConfig.class);
    }

    private Optional<CoverityToolInstallation[]> getGlobalCoverityToolInstallations() {
        return Optional.ofNullable(getCoverityGlobalConfig())
                   .map(CoverityGlobalConfig::getCoverityToolInstallations);
    }

    private Optional<CoverityConnectInstance[]> getGlobalCoverityConnectInstances() {
        return Optional.ofNullable(getCoverityGlobalConfig())
                   .map(CoverityGlobalConfig::getCoverityConnectInstances);
    }

}
