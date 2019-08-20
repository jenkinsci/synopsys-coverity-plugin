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

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.coverity.ws.v9.ProjectDataObj;
import com.synopsys.integration.coverity.ws.v9.StreamDataObj;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.extensions.CoveritySelectBoxEnum;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.Slf4jIntLogger;

import hudson.util.ListBoxModel;

public class CommonFieldValueProvider {
    private final Logger logger = LoggerFactory.getLogger(CommonFieldValueProvider.class);

    private final ProjectCacheData projectCacheData = new ProjectCacheData();
    private final ViewCacheData viewCacheData = new ViewCacheData();

    public static ListBoxModel getListBoxModelOf(final CoveritySelectBoxEnum[] coveritySelectBoxEnumValues) {
        return Stream.of(coveritySelectBoxEnumValues)
                   .collect(ListBoxModel::new, (model, value) -> model.add(value.getDisplayName(), value.name()), ListBoxModel::addAll);
    }

    public ListBoxModel doFillCoverityInstanceUrlItems() {
        final ListBoxModel listBoxModel = GlobalValueHelper.getGlobalCoverityConnectInstances().stream()
                                              .map(CoverityConnectInstance::getUrl)
                                              .map(this::wrapAsListBoxModelOption)
                                              .collect(ListBoxModel::new, ListBoxModel::add, ListBoxModel::addAll);
        listBoxModel.add("- none -", "");
        return listBoxModel;
    }

    public ListBoxModel doFillProjectNameItems(final String jenkinsCoverityInstanceUrl, final Boolean updateNow) {
        if (checkAndWaitForProjectCacheData(jenkinsCoverityInstanceUrl, updateNow)) {
            return projectCacheData.getCachedData().stream()
                       .map(this::toProjectName)
                       .filter(StringUtils::isNotBlank)
                       .map(this::wrapAsListBoxModelOption)
                       .collect(Collectors.toCollection(ListBoxModel::new));
        }

        return new ListBoxModel();
    }

    public ListBoxModel doFillStreamNameItems(final String jenkinsCoverityInstanceUrl, final String selectedProjectName, final Boolean updateNow) {
        if (checkAndWaitForProjectCacheData(jenkinsCoverityInstanceUrl, updateNow)) {
            return projectCacheData.getCachedData().stream()
                       .filter(projectDataObj -> isMatchingProject(projectDataObj, selectedProjectName))
                       .flatMap(this::toProjectStreams)
                       .map(this::toStreamName)
                       .filter(StringUtils::isNotBlank)
                       .map(this::wrapAsListBoxModelOption)
                       .collect(Collectors.toCollection(ListBoxModel::new));
        }

        return new ListBoxModel();
    }

    public ListBoxModel doFillViewNameItems(final String jenkinsCoverityInstanceUrl, final Boolean updateNow) {
        if (checkAndWaitForViewCacheData(jenkinsCoverityInstanceUrl, updateNow)) {
            return viewCacheData.getCachedData().stream()
                       .filter(StringUtils::isNotBlank)
                       .map(this::wrapAsListBoxModelOption)
                       .collect(Collectors.toCollection(ListBoxModel::new));
        }

        return new ListBoxModel();
    }

    private String toProjectName(final ProjectDataObj projectDataObj) {
        if (projectDataObj != null && projectDataObj.getId() != null) {
            return projectDataObj.getId().getName();
        }
        return null;
    }

    private Stream<StreamDataObj> toProjectStreams(final ProjectDataObj projectDataObj) {
        return projectDataObj.getStreams().stream()
                   .filter(Objects::nonNull);
    }

    private String toStreamName(final StreamDataObj streamDataObj) {
        if (streamDataObj != null && streamDataObj.getId() != null) {
            return streamDataObj.getId().getName();
        }
        return null;
    }

    private Boolean isMatchingProject(final ProjectDataObj projectDataObj, final String selectedProjectName) {
        return null != projectDataObj
                   && null != projectDataObj.getId()
                   && null != projectDataObj.getId().getName()
                   && projectDataObj.getId().getName().equals(selectedProjectName);
    }

    private ListBoxModel.Option wrapAsListBoxModelOption(final String nameValue) {
        return new ListBoxModel.Option(nameValue, nameValue, false);
    }

    private boolean checkAndWaitForProjectCacheData(final String jenkinsCoverityInstanceUrl, final Boolean updateNow) {
        return checkAndWaitForCacheData(jenkinsCoverityInstanceUrl, updateNow, projectCacheData);
    }

    private boolean checkAndWaitForViewCacheData(final String jenkinsCoverityInstanceUrl, final Boolean updateNow) {
        return checkAndWaitForCacheData(jenkinsCoverityInstanceUrl, updateNow, viewCacheData);
    }

    private boolean checkAndWaitForCacheData(final String jenkinsCoverityInstanceUrl, final Boolean updateNow, final BaseCacheData cacheData) {
        final CoverityConnectInstance coverityConnectInstance = GlobalValueHelper.getCoverityInstanceWithUrl(new Slf4jIntLogger(logger), jenkinsCoverityInstanceUrl).orElse(null);
        if (coverityConnectInstance != null) {
            try {
                cacheData.checkAndWaitForData(coverityConnectInstance, updateNow);
                return true;
            } catch (final IllegalArgumentException | IllegalStateException ignored) {
                // Handled by form validation
            } catch (final Exception e) {
                logger.warn("Unexpected exception encountered when checking or waiting for Coverity data: " + e.getMessage());
                logger.trace("Stack trace:", e);
            }
        }

        return false;
    }

}
