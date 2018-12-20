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
package com.synopsys.integration.jenkins.coverity.extensions.global;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import com.synopsys.integration.coverity.CoverityVersion;
import com.synopsys.integration.jenkins.coverity.extensions.global.tools.CoverityToolInstallation;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

@Extension
public class CoverityGlobalConfig extends GlobalConfiguration {
    public static final CoverityVersion MINIMUM_SUPPORTED_VERSION = CoverityVersion.VERSION_JASPER;

    private CoverityConnectInstance[] coverityConnectInstances;
    private CoverityToolInstallation[] coverityToolInstallations;

    @DataBoundConstructor
    public CoverityGlobalConfig() {
        load();
    }

    @Override
    public boolean configure(final StaplerRequest req, final JSONObject formData) throws Descriptor.FormException {
        req.bindJSON(this, formData);
        save();
        return super.configure(req, formData);
    }

    public CoverityToolInstallation[] getCoverityToolInstallations() {
        return coverityToolInstallations;
    }

    @DataBoundSetter
    public void setCoverityToolInstallations(final CoverityToolInstallation[] coverityToolInstallations) {
        this.coverityToolInstallations = coverityToolInstallations;
        save();
    }

    public CoverityConnectInstance[] getCoverityConnectInstances() {
        return coverityConnectInstances;
    }

    @DataBoundSetter
    public void setCoverityConnectInstances(final CoverityConnectInstance[] coverityConnectInstances) {
        this.coverityConnectInstances = coverityConnectInstances;
        save();
    }

}
