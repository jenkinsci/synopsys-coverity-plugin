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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.ws.WebServiceException;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.JenkinsCoverityInstance;
import com.synopsys.integration.coverity.Messages;
import com.synopsys.integration.coverity.common.cache.ProjectCacheData;
import com.synopsys.integration.coverity.common.cache.ViewCacheData;
import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.config.CoverityServerConfigBuilder;
import com.synopsys.integration.coverity.config.CoverityServerConfigValidator;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.freestyle.CoverityBuildStepDescriptor;
import com.synopsys.integration.coverity.tools.CoverityToolInstallation;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.v9.ProjectDataObj;
import com.synopsys.integration.coverity.ws.v9.StreamDataObj;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.LogLevel;
import com.synopsys.integration.log.PrintStreamIntLogger;
import com.synopsys.integration.validator.FieldEnum;
import com.synopsys.integration.validator.ValidationResult;
import com.synopsys.integration.validator.ValidationResults;

import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class CoverityCommonDescriptor {
    private final ProjectCacheData projectCacheData = new ProjectCacheData();
    private final ViewCacheData viewCacheData = new ViewCacheData();

    public ListBoxModel doFillCoverityToolNameItems(final CoverityToolInstallation[] coverityToolInstallations) {
        final ListBoxModel boxModel;

        if (null == coverityToolInstallations) {
            boxModel = new ListBoxModel();
        } else {
            boxModel = Arrays.stream(coverityToolInstallations)
                           .map(CoverityToolInstallation::getName)
                           .collect(ListBoxModel::new, ListBoxModel::add, ListBoxModel::addAll);
        }

        boxModel.add(Messages.CoverityToolInstallation_getNone(), "");
        return boxModel;
    }

    public FormValidation doCheckCoverityToolName(final CoverityToolInstallation[] coverityToolInstallations, final String coverityToolName) {
        if (null == coverityToolInstallations || coverityToolInstallations.length == 0) {
            return FormValidation.error(Messages.CoverityToolInstallation_getNoToolsConfigured());
        }
        if (StringUtils.isBlank(coverityToolName)) {
            return FormValidation.error(Messages.CoverityToolInstallation_getPleaseChooseATool());
        }

        final boolean hasMatchingToolName = Arrays.stream(coverityToolInstallations)
                                                .map(CoverityToolInstallation::getName)
                                                .anyMatch(coverityToolName::equals);

        if (hasMatchingToolName) {
            return FormValidation.ok();
        }
        return FormValidation.error(Messages.CoverityToolInstallation_getNoToolWithName_0(coverityToolName));
    }

    public ListBoxModel doFillBuildStatusForIssuesItems() {
        return this.getListBoxModelOf(BuildStatus.values());
    }

    public ListBoxModel doFillCoverityAnalysisTypeItems() {
        return this.getListBoxModelOf(CoverityAnalysisType.values());
    }

    public ListBoxModel doFillOnCommandFailureItems() {
        return this.getListBoxModelOf(OnCommandFailure.values());
    }

    public ListBoxModel doFillProjectNameItems(final String projectName, final Boolean updateNow) {
        final ListBoxModel boxModel = new ListBoxModel();
        try {
            final Optional<JenkinsCoverityInstance> optionalCoverityInstance = getCoverityInstance();
            if (!optionalCoverityInstance.isPresent()) {
                return boxModel;
            }

            try {
                projectCacheData.checkAndWaitForData(optionalCoverityInstance.get(), updateNow);
            } catch (IntegrationException | InterruptedException e) {
                e.printStackTrace();
                return boxModel;
            }

            for (final ProjectDataObj project : projectCacheData.getCachedData()) {
                if (null != project.getId() && null != project.getId().getName()) {
                    final String currentProjectName = project.getId().getName();
                    if (StringUtils.isNotBlank(projectName) && isMatchingProject(project, projectName)) {
                        boxModel.add(new ListBoxModel.Option(currentProjectName, currentProjectName, true));
                    } else {
                        boxModel.add(currentProjectName);
                    }
                }
            }
        } catch (final IllegalStateException ignored) {
            // Handled by form validation
        }
        return boxModel;
    }

    public ListBoxModel doFillStreamNameItems(final String projectName, final String streamName, final Boolean updateNow) {
        final ListBoxModel boxModel = new ListBoxModel();
        try {
            if (StringUtils.isBlank(projectName)) {
                return boxModel;
            }
            final Optional<JenkinsCoverityInstance> optionalCoverityInstance = getCoverityInstance();
            if (!optionalCoverityInstance.isPresent()) {
                return boxModel;
            }

            try {
                projectCacheData.checkAndWaitForData(optionalCoverityInstance.get(), updateNow);
            } catch (IntegrationException | InterruptedException e) {
                e.printStackTrace();
                return boxModel;
            }

            for (final ProjectDataObj project : projectCacheData.getCachedData()) {
                if (isMatchingProject(project, projectName) && null != project.getStreams() && !project.getStreams().isEmpty()) {
                    for (final StreamDataObj stream : project.getStreams()) {
                        if (null != stream.getId() && null != stream.getId().getName()) {
                            final String currentStreamName = stream.getId().getName();
                            if (StringUtils.isNotBlank(streamName) && currentStreamName.equals(streamName)) {
                                boxModel.add(new ListBoxModel.Option(currentStreamName, currentStreamName, true));
                            } else {
                                boxModel.add(currentStreamName);
                            }
                        }
                    }
                }
            }
        } catch (final IllegalStateException ignored) {
            // Handled by form validation
        }
        return boxModel;
    }

    private Boolean isMatchingProject(final ProjectDataObj projectDataObj, final String projectName) {
        return null != projectDataObj && null != projectDataObj.getId() && null != projectDataObj.getId().getName() && projectDataObj.getId().getName().equals(projectName);
    }

    public ListBoxModel doFillViewNameItems(final String viewName, final Boolean updateNow) {
        final ListBoxModel boxModel = new ListBoxModel();
        final Optional<JenkinsCoverityInstance> optionalCoverityInstance = getCoverityInstance();

        if (!optionalCoverityInstance.isPresent()) {
            return boxModel;
        }

        try {
            viewCacheData.checkAndWaitForData(optionalCoverityInstance.get(), updateNow);
        } catch (IntegrationException | InterruptedException e) {
            e.printStackTrace();
            return boxModel;
        }

        for (final String view : viewCacheData.getCachedData()) {
            if (StringUtils.isNotBlank(viewName) && view.equals(viewName)) {
                boxModel.add(new ListBoxModel.Option(view, view, true));
            } else {
                boxModel.add(view);
            }
        }
        return boxModel;
    }

    public FormValidation testConnectionSilently() {
        final FormValidation connectionTest = this.testConnectionToDefaultCoverityInstance();
        if (FormValidation.Kind.OK.equals(connectionTest.kind)) {
            return FormValidation.ok();
        } else {
            return connectionTest;
        }
    }

    public FormValidation testConnectionToCoverityInstance(final JenkinsCoverityInstance jenkinsCoverityInstance) {
        final String url = jenkinsCoverityInstance.getCoverityURL().map(URL::toString).orElse(null);
        final String username = jenkinsCoverityInstance.getCoverityUsername().orElse(null);
        final String password = jenkinsCoverityInstance.getCoverityPassword().orElse(null);

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

    private ListBoxModel getListBoxModelOf(final CoveritySelectBoxEnum[] coveritySelectBoxEnumValues) {
        return Stream.of(coveritySelectBoxEnumValues)
                   .collect(ListBoxModel::new, (model, value) -> model.add(value.getDisplayName(), value.name()), ListBoxModel::addAll);
    }

    private FormValidation testConnectionToDefaultCoverityInstance() {
        return getCoverityInstance().map(this::testConnectionToCoverityInstance)
                   .orElse(FormValidation.error("Could not connect to Coverity server, no configured Coverity server was detected in the Jenkins System Configuration."));
    }

    private CoverityBuildStepDescriptor getCoverityPostBuildStepDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(CoverityBuildStepDescriptor.class);
    }

    private Optional<JenkinsCoverityInstance> getCoverityInstance() {
        return getCoverityPostBuildStepDescriptor().getCoverityInstance();
    }

}
