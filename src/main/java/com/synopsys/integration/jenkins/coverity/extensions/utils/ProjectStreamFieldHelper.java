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
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.ws.v9.ProjectDataObj;
import com.synopsys.integration.coverity.ws.v9.StreamDataObj;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.IntLogger;

import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class ProjectStreamFieldHelper extends ConnectionCachingFieldHelper<ProjectStreamCache> {
    public ProjectStreamFieldHelper(final IntLogger logger) {
        super(logger, () -> new ProjectStreamCache(logger));
    }

    public ComboBoxModel getProjectNamesForComboBox(final String jenkinsCoverityInstanceUrl) throws InterruptedException {
        return doFillProjectNameItems(ComboBoxModel::new, Function.identity(), jenkinsCoverityInstanceUrl);
    }

    public ListBoxModel getProjectNamesForListBox(final String jenkinsCoverityInstanceUrl) throws InterruptedException {
        return doFillProjectNameItems(ListBoxModel::new, this::wrapAsListBoxModelOption, jenkinsCoverityInstanceUrl);
    }

    public ComboBoxModel getStreamNamesForComboBox(final String jenkinsCoverityInstanceUrl, final String selectedProjectName) throws InterruptedException {
        try {
            return getStreams(jenkinsCoverityInstanceUrl, selectedProjectName).stream()
                       .map(this::toStreamName)
                       .filter(StringUtils::isNotBlank)
                       .collect(Collectors.toCollection(ComboBoxModel::new));

        } catch (final CoverityIntegrationException ignored) {
            // Form validation will display this exception as an error, so we can safely ignore it
            return new ComboBoxModel();
        }
    }

    public FormValidation checkForProjectInCache(final String coverityConnectUrl, final String projectName) {
        try {
            return getProjects(coverityConnectUrl).stream()
                       .map(this::toProjectName)
                       .filter(projectName::equals)
                       .findFirst()
                       .map(ignored -> FormValidation.ok())
                       .orElseGet(() -> FormValidation.warning(String.format("If project '%s' does not exist it will be created with defaults the next time this job is run.", projectName)));
        } catch (final CoverityIntegrationException e) {
            return FormValidation.error(e, e.getMessage());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return FormValidation.error(e, e.getMessage());
        }
    }

    public FormValidation checkForStreamInCache(final String coverityConnectUrl, final String projectName, final String streamName) {
        try {
            return getStreams(coverityConnectUrl, projectName).stream()
                       .map(this::toStreamName)
                       .filter(streamName::equals)
                       .findFirst()
                       .map(ignored -> FormValidation.ok())
                       .orElseGet(() -> FormValidation.warning(String.format("If stream '%s' does not exist in project '%s' it will be created with defaults the next time this job is run", streamName, projectName)));
        } catch (final CoverityIntegrationException e) {
            return FormValidation.error(e, e.getMessage());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return FormValidation.error(e, e.getMessage());
        }
    }

    private <T, R extends Collection<T>> R doFillProjectNameItems(final Supplier<R> supplier, final Function<String, T> itemWrapper, final String jenkinsCoverityInstanceUrl) throws InterruptedException {
        try {
            return getProjects(jenkinsCoverityInstanceUrl).stream()
                       .map(this::toProjectName)
                       .filter(StringUtils::isNotBlank)
                       .map(itemWrapper)
                       .collect(Collectors.toCollection(supplier));
        } catch (final CoverityIntegrationException ignored) {
            // Form validation will display this exception as an error, so we can safely ignore it
            return supplier.get();
        }
    }

    private List<ProjectDataObj> getProjects(final String coverityConnectUrl) throws CoverityIntegrationException, InterruptedException {
        final CoverityConnectInstance coverityConnectInstance = GlobalValueHelper.getCoverityInstanceWithUrlOrDie(logger, coverityConnectUrl);
        final ProjectStreamCache projectStreamCache = getCache(coverityConnectUrl);
        return projectStreamCache.getData(coverityConnectInstance);
    }

    private List<StreamDataObj> getStreams(final String coverityConnectUrl, final String projectName) throws CoverityIntegrationException, InterruptedException {
        return getProjects(coverityConnectUrl).stream()
                   .filter(projectDataObj -> this.isMatchingProject(projectDataObj, projectName))
                   .map(ProjectDataObj::getStreams)
                   .flatMap(Collection::stream)
                   .collect(Collectors.toList());
    }

    private Boolean isMatchingProject(final ProjectDataObj projectDataObj, final String selectedProjectName) {
        return null != projectDataObj
                   && null != projectDataObj.getId()
                   && null != projectDataObj.getId().getName()
                   && projectDataObj.getId().getName().equals(selectedProjectName);
    }

    private String toStreamName(final StreamDataObj streamDataObj) {
        if (streamDataObj != null && streamDataObj.getId() != null) {
            return streamDataObj.getId().getName();
        }
        return null;
    }

    private String toProjectName(final ProjectDataObj projectDataObj) {
        if (projectDataObj != null && projectDataObj.getId() != null) {
            return projectDataObj.getId().getName();
        }
        return null;
    }
}
