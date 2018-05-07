/**
 * sig-coverity
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
package com.sig.integration.coverity.common;

import org.apache.commons.lang3.StringUtils;

import com.sig.integration.coverity.Messages;
import com.sig.integration.coverity.tools.CoverityToolInstallation;

import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CoverityCommonDescriptor {

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
}
