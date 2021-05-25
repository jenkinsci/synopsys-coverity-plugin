/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.StreamDataObj;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.IntLogger;

import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class ProjectStreamFieldHelper extends ConnectionCachingFieldHelper<ProjectStreamCache> {
    public ProjectStreamFieldHelper(IntLogger logger) {
        super(logger, () -> new ProjectStreamCache(logger));
    }

    public ComboBoxModel getProjectNamesForComboBox(String coverityInstanceUrl, Boolean overrideDefaultCredentialsId, String credentialsId) throws InterruptedException {
        return doFillProjectNameItems(ComboBoxModel::new, Function.identity(), coverityInstanceUrl, overrideDefaultCredentialsId, credentialsId);
    }

    public ListBoxModel getProjectNamesForListBox(String coverityConnectUrl, Boolean overrideDefaultCredentialsId, String credentialsId) throws InterruptedException {
        return doFillProjectNameItems(ListBoxModel::new, this::wrapAsListBoxModelOption, coverityConnectUrl, overrideDefaultCredentialsId, credentialsId);
    }

    public ComboBoxModel getStreamNamesForComboBox(String coverityInstanceUrl, Boolean overrideDefaultCredentialsId, String credentialsId, String selectedProjectName) throws InterruptedException {
        try {
            return getStreams(coverityInstanceUrl, overrideDefaultCredentialsId, credentialsId, selectedProjectName).stream()
                       .map(this::toStreamName)
                       .filter(StringUtils::isNotBlank)
                       .collect(Collectors.toCollection(ComboBoxModel::new));

        } catch (CoverityIntegrationException ignored) {
            // Form validation will display this exception as an error, so we can safely ignore it
            return new ComboBoxModel();
        }
    }


    public FormValidation checkForProjectInCache(String coverityInstanceUrl, Boolean overrideDefaultCredentialsId, String credentialsId, String projectName) {
        try {
            return getProjects(coverityInstanceUrl, overrideDefaultCredentialsId, credentialsId).stream()
                       .map(this::toProjectName)
                       .filter(projectName::equals)
                       .findFirst()
                       .map(ignored -> FormValidation.ok())
                       .orElseGet(() -> FormValidation.warning(String.format("If project '%s' does not exist it will be created with defaults the next time this job is run.", projectName)));
        } catch (CoverityIntegrationException e) {
            return FormValidation.error(e, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FormValidation.error(e, e.getMessage());
        }
    }

    public FormValidation checkForStreamInCache(String coverityInstanceUrl, Boolean overrideDefaultCredentialsId, String credentialsId, String projectName, String streamName) {
        try {
            return getStreams(coverityInstanceUrl, overrideDefaultCredentialsId, credentialsId, projectName).stream()
                       .map(this::toStreamName)
                       .filter(streamName::equals)
                       .findFirst()
                       .map(ignored -> FormValidation.ok())
                       .orElseGet(() -> FormValidation.warning(String.format("If stream '%s' does not exist in project '%s' it will be created with defaults the next time this job is run", streamName, projectName)));
        } catch (CoverityIntegrationException e) {
            return FormValidation.error(e, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FormValidation.error(e, e.getMessage());
        }
    }

    private <T, R extends Collection<T>> R doFillProjectNameItems(Supplier<R> supplier, Function<String, T> itemWrapper, String coverityInstanceUrl, Boolean overrideDefaultCredentialsId, String credentialsId) throws InterruptedException {
        try {
            return getProjects(coverityInstanceUrl, overrideDefaultCredentialsId, credentialsId).stream()
                       .map(this::toProjectName)
                       .filter(StringUtils::isNotBlank)
                       .map(itemWrapper)
                       .collect(Collectors.toCollection(supplier));
        } catch (CoverityIntegrationException ignored) {
            // Form validation will display this exception as an error, so we can safely ignore it
            return supplier.get();
        }
    }

    private List<StreamDataObj> getStreams(String coverityInstanceUrl, Boolean overrideDefaultCredentialsId, String credentialsId, String projectName) throws CoverityIntegrationException, InterruptedException {
        return getProjects(coverityInstanceUrl, overrideDefaultCredentialsId, credentialsId).stream()
                   .filter(projectDataObj -> this.isMatchingProject(projectDataObj, projectName))
                   .map(ProjectDataObj::getStreams)
                   .flatMap(Collection::stream)
                   .collect(Collectors.toList());
    }

    private List<ProjectDataObj> getProjects(String coverityInstanceUrl, Boolean overrideDefaultCredentialsId, String credentialsId) throws CoverityIntegrationException, InterruptedException {
        CoverityConnectInstance coverityConnectInstance = GlobalValueHelper.getCoverityInstanceWithUrlOrDie(logger, coverityInstanceUrl);
        ProjectStreamCache projectStreamCache;
        List<ProjectDataObj> projectDataObjs;
        if (Boolean.TRUE.equals(overrideDefaultCredentialsId)) {
            projectStreamCache = getCache(coverityInstanceUrl, credentialsId);
            projectDataObjs = projectStreamCache.getData(credentialsId, coverityConnectInstance);
        } else {
            projectStreamCache = getCache(coverityInstanceUrl, coverityConnectInstance.getDefaultCredentialsId());
            projectDataObjs = projectStreamCache.getData(coverityConnectInstance.getDefaultCredentialsId(), coverityConnectInstance);
        }
        return projectDataObjs != null ? projectDataObjs : Collections.emptyList();
    }

    private Boolean isMatchingProject(ProjectDataObj projectDataObj, String selectedProjectName) {
        return null != projectDataObj
                   && null != projectDataObj.getId()
                   && null != projectDataObj.getId().getName()
                   && projectDataObj.getId().getName().equals(selectedProjectName);
    }

    private String toStreamName(StreamDataObj streamDataObj) {
        if (streamDataObj != null && streamDataObj.getId() != null) {
            return streamDataObj.getId().getName();
        }
        return null;
    }

    private String toProjectName(ProjectDataObj projectDataObj) {
        if (projectDataObj != null && projectDataObj.getId() != null) {
            return projectDataObj.getId().getName();
        }
        return null;
    }
}
