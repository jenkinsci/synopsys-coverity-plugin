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

import java.util.List;

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

import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class CoverityCommonDescriptor {
    private ProjectCacheData projectCacheData = new ProjectCacheData();
    private ViewCacheData viewCacheData = new ViewCacheData();

    public ListBoxModel doFillCoverityToolNameItems(CoverityToolInstallation[] coverityToolInstallations) {
        ListBoxModel boxModel = new ListBoxModel();
        boxModel.add(Messages.CoverityToolInstallation_getNone(), "");
        if (null != coverityToolInstallations && coverityToolInstallations.length > 0) {
            for (CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
                boxModel.add(coverityToolInstallation.getName());
            }
        }
        return boxModel;
    }

    public FormValidation doCheckCoverityToolName(CoverityToolInstallation[] coverityToolInstallations, String coverityToolName) {
        if (null == coverityToolInstallations || coverityToolInstallations.length == 0) {
            return FormValidation.error(Messages.CoverityToolInstallation_getNoToolsConfigured());
        }
        if (StringUtils.isBlank(coverityToolName)) {
            return FormValidation.error(Messages.CoverityToolInstallation_getPleaseChooseATool());
        }
        for (CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
            if (coverityToolInstallation.getName().equals(coverityToolName)) {
                return FormValidation.ok();
            }
        }
        return FormValidation.error(Messages.CoverityToolInstallation_getNoToolWithName_0(coverityToolName));
    }

    public ListBoxModel doFillBuildStateForIssuesItems() {
        ListBoxModel boxModel = new ListBoxModel();
        for (BuildState buildState : BuildState.values()) {
            boxModel.add(buildState.getDisplayValue());
        }
        return boxModel;
    }

    public AutoCompletionCandidates doAutoCompleteProjectName(String projectName) {
        AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
        if (StringUtils.isNotBlank(projectName)) {
            JenkinsCoverityInstance coverityInstance = getCoverityInstance();
            if (null == coverityInstance || coverityInstance.isEmpty()) {
                return autoCompletionCandidates;
            }
            try {
                projectCacheData.checkAndWaitForData(coverityInstance);
            } catch (IntegrationException | InterruptedException e) {
                e.printStackTrace();
                return autoCompletionCandidates;
            }
            for (ProjectDataObj project : projectCacheData.getCachedData()) {
                if (null != project.getId() && null != project.getId().getName() && project.getId().getName().startsWith(projectName)) {
                    autoCompletionCandidates.add(project.getId().getName());
                }
            }

        }
        return autoCompletionCandidates;
    }

    public FormValidation doCheckProjectName(String projectName) {
        if (StringUtils.isBlank(projectName)) {
            return FormValidation.ok();
        }
        if (projectName.contains("$")) {
            return FormValidation.warning(
                    String.format("The name \"%s\" appears to contain a variable which can only be resolved at the time of the build.", projectName));
        }
        JenkinsCoverityInstance coverityInstance = getCoverityInstance();
        if (null == coverityInstance || coverityInstance.isEmpty()) {
            return FormValidation.error("Global coverity instance is not configured.");
        }
        try {
            projectCacheData.checkAndWaitForData(coverityInstance);
        } catch (IntegrationException e) {
            return FormValidation.error(e.getMessage());
        } catch (InterruptedException e) {
            return FormValidation.error(e.toString());
        }
        List<ProjectDataObj> projects = projectCacheData.getCachedData();
        for (ProjectDataObj project : projects) {
            if (null != project.getId() && null != project.getId().getName() && project.getId().getName().equals(projectName)) {
                return FormValidation.ok();
            }
        }
        return FormValidation.error(String.format("The project \"%s\" does not exist or you do not have permission to access it.", projectName));
    }

    public AutoCompletionCandidates doAutoCompleteStreamName(String streamName) {
        AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
        if (StringUtils.isBlank(streamName)) {
            return autoCompletionCandidates;
        }
        JenkinsCoverityInstance coverityInstance = getCoverityInstance();
        if (null == coverityInstance || coverityInstance.isEmpty()) {
            return autoCompletionCandidates;
        }
        try {
            projectCacheData.checkAndWaitForData(coverityInstance);
        } catch (IntegrationException | InterruptedException e) {
            e.printStackTrace();
            return autoCompletionCandidates;
        }
        for (ProjectDataObj project : projectCacheData.getCachedData()) {
            if (null != project.getStreams() && !project.getStreams().isEmpty()) {
                for (StreamDataObj stream : project.getStreams()) {
                    if (null != stream.getId() && null != stream.getId().getName()) {
                        String currentStreamName = stream.getId().getName();
                        if (currentStreamName.startsWith(streamName)) {
                            autoCompletionCandidates.add(currentStreamName);
                        }
                    }
                }
            }
        }
        return autoCompletionCandidates;
    }

    public FormValidation doCheckStreamName(String projectName, String streamName) {
        if (StringUtils.isBlank(projectName) || StringUtils.isBlank(streamName)) {
            return FormValidation.ok();
        }
        if (streamName.contains("$")) {
            return FormValidation.warning(
                    String.format("The name \"%s\" appears to contain a variable which can only be resolved at the time of the build.", streamName));
        }
        JenkinsCoverityInstance coverityInstance = getCoverityInstance();
        if (null == coverityInstance || coverityInstance.isEmpty()) {
            return FormValidation.error("Global coverity instance is not configured.");
        }
        try {
            projectCacheData.checkAndWaitForData(coverityInstance);
        } catch (IntegrationException e) {
            return FormValidation.error(e.getMessage());
        } catch (InterruptedException e) {
            return FormValidation.error(e.toString());
        }
        List<ProjectDataObj> projects = projectCacheData.getCachedData();
        for (ProjectDataObj project : projects) {
            if (null != project.getId() && null != project.getId().getName() && project.getId().getName().equals(projectName)) {
                if (null != project.getStreams() && !project.getStreams().isEmpty()) {
                    for (StreamDataObj stream : project.getStreams()) {
                        if (null != stream.getId() && null != stream.getId().getName()) {
                            String currentStreamName = stream.getId().getName();
                            if (currentStreamName.equals(streamName)) {
                                return FormValidation.ok();
                            }
                        }
                    }
                }
            }
        }
        return FormValidation.error(String.format("The stream \"%s\" does not exist for project \"%s\" or you do not have permission to access it.", streamName, projectName));
    }

    public AutoCompletionCandidates doAutoCompleteViewName(String viewName) {
        AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
        JenkinsCoverityInstance coverityInstance = getCoverityInstance();
        if (null == coverityInstance || coverityInstance.isEmpty() || StringUtils.isBlank(viewName)) {
            return autoCompletionCandidates;
        }
        try {
            viewCacheData.checkAndWaitForData(coverityInstance);
        } catch (IntegrationException | InterruptedException e) {
            e.printStackTrace();
            return autoCompletionCandidates;
        }
        for (String view : viewCacheData.getCachedData()) {
            if (view.startsWith(viewName)) {
                autoCompletionCandidates.add(view);
            }
        }
        return autoCompletionCandidates;
    }

    public FormValidation doCheckViewName(String viewName) {
        if (StringUtils.isBlank(viewName)) {
            return FormValidation.ok();
        }
        if (viewName.contains("$")) {
            return FormValidation.warning(
                    String.format("The name \"%s\" appears to contain a variable which can only be resolved at the time of the build.", viewName));
        }
        JenkinsCoverityInstance coverityInstance = getCoverityInstance();
        if (null == coverityInstance || coverityInstance.isEmpty()) {
            return FormValidation.error("Global coverity instance is not configured.");
        }
        try {
            viewCacheData.checkAndWaitForData(coverityInstance);
        } catch (IntegrationException e) {
            return FormValidation.error(e.getMessage());
        } catch (InterruptedException e) {
            return FormValidation.error(e.toString());
        }
        for (String view : viewCacheData.getCachedData()) {
            if (view.equals(viewName)) {
                return FormValidation.ok();
            }
        }
        return FormValidation.error(String.format("The view \"%s\" does not exist or you do not have permission to access it.", viewName));
    }

    public CoverityPostBuildStepDescriptor getCoverityPostBuildStepDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(CoverityPostBuildStepDescriptor.class);
    }

    public JenkinsCoverityInstance getCoverityInstance() {
        return getCoverityPostBuildStepDescriptor().getCoverityInstance();
    }
}
