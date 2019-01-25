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
package com.synopsys.integration.jenkins.coverity.extensions.global;

import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;

@Extension
public class CoverityGlobalConfig extends GlobalConfiguration {
    private List<CoverityConnectInstance> coverityConnectInstances;
    private List<CoverityToolInstallation> coverityToolInstallations;

    @DataBoundConstructor
    public CoverityGlobalConfig() {
        load();
    }

    public List<CoverityToolInstallation> getCoverityToolInstallations() {
        return coverityToolInstallations;
    }

    @DataBoundSetter
    public void setCoverityToolInstallations(final List<CoverityToolInstallation> coverityToolInstallations) {
        this.coverityToolInstallations = coverityToolInstallations;
        save();
    }

    public List<CoverityConnectInstance> getCoverityConnectInstances() {
        return coverityConnectInstances;
    }

    @DataBoundSetter
    public void setCoverityConnectInstances(final List<CoverityConnectInstance> coverityConnectInstances) {
        this.coverityConnectInstances = coverityConnectInstances;
        save();
    }

}
