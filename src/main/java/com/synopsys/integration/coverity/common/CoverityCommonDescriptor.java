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

import org.apache.commons.lang3.StringUtils;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.synopsys.integration.coverity.JenkinsCoverityInstance;
import com.synopsys.integration.coverity.Messages;
import com.synopsys.integration.coverity.common.cache.ProjectCacheData;
import com.synopsys.integration.coverity.common.cache.ViewCacheData;
import com.synopsys.integration.coverity.post.CoverityPostBuildStepDescriptor;
import com.synopsys.integration.coverity.tools.CoverityToolInstallation;
import com.synopsys.integration.coverity.ws.v9.ProjectDataObj;
import com.synopsys.integration.coverity.ws.v9.StreamDataObj;

import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class CoverityCommonDescriptor {
    private final ProjectCacheData projectCacheData = new ProjectCacheData();
    private final ViewCacheData viewCacheData = new ViewCacheData();

    public ListBoxModel doFillCoverityToolNameItems(final CoverityToolInstallation[] coverityToolInstallations) {
        final ListBoxModel boxModel = new ListBoxModel();
        boxModel.add(Messages.CoverityToolInstallation_getNone(), "");
        if (null != coverityToolInstallations && coverityToolInstallations.length > 0) {
            for (final CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
                boxModel.add(coverityToolInstallation.getName());
            }
        }
        return boxModel;
    }

    public FormValidation doCheckCoverityToolName(final CoverityToolInstallation[] coverityToolInstallations, final String coverityToolName) {
        if (null == coverityToolInstallations || coverityToolInstallations.length == 0) {
            return FormValidation.error(Messages.CoverityToolInstallation_getNoToolsConfigured());
        }
        if (StringUtils.isBlank(coverityToolName)) {
            return FormValidation.error(Messages.CoverityToolInstallation_getPleaseChooseATool());
        }
        for (final CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
            if (coverityToolInstallation.getName().equals(coverityToolName)) {
                return FormValidation.ok();
            }
        }
        return FormValidation.error(Messages.CoverityToolInstallation_getNoToolWithName_0(coverityToolName));
    }

    public ListBoxModel doFillBuildStateForIssuesItems() {
        final ListBoxModel boxModel = new ListBoxModel();
        boxModel.add(BuildState.NONE.getDisplayValue(), BuildState.NONE.name());
        for (final BuildState buildState : BuildState.values()) {
            if (BuildState.NONE != buildState) {
                boxModel.add(buildState.getDisplayValue(), buildState.name());
            }
        }
        return boxModel;
    }

    public ListBoxModel doFillProjectNameItems(final String projectName, final Boolean updateNow) {
        final ListBoxModel boxModel = new ListBoxModel();
        final JenkinsCoverityInstance coverityInstance = getCoverityInstance();
        if (null == coverityInstance || coverityInstance.isEmpty()) {
            return boxModel;
        }
        try {
            projectCacheData.checkAndWaitForData(coverityInstance, updateNow);
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
        return boxModel;
    }

    public ListBoxModel doFillStreamNameItems(final String projectName, final String streamName, final Boolean updateNow) {
        final ListBoxModel boxModel = new ListBoxModel();
        if (StringUtils.isBlank(projectName)) {
            return boxModel;
        }
        final JenkinsCoverityInstance coverityInstance = getCoverityInstance();
        if (null == coverityInstance || coverityInstance.isEmpty()) {
            return boxModel;
        }
        try {
            projectCacheData.checkAndWaitForData(coverityInstance, updateNow);
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
        return boxModel;
    }

    private Boolean isMatchingProject(final ProjectDataObj projectDataObj, final String projectName) {
        return null != projectDataObj && null != projectDataObj.getId() && null != projectDataObj.getId().getName() && projectDataObj.getId().getName().equals(projectName);

    }

    public ListBoxModel doFillViewNameItems(final String viewName, final Boolean updateNow) {
        final ListBoxModel boxModel = new ListBoxModel();
        final JenkinsCoverityInstance coverityInstance = getCoverityInstance();
        if (null == coverityInstance || coverityInstance.isEmpty()) {
            return boxModel;
        }
        try {
            viewCacheData.checkAndWaitForData(coverityInstance, updateNow);
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

    private CoverityPostBuildStepDescriptor getCoverityPostBuildStepDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(CoverityPostBuildStepDescriptor.class);
    }

    private JenkinsCoverityInstance getCoverityInstance() {
        return getCoverityPostBuildStepDescriptor().getCoverityInstance();
    }
}
