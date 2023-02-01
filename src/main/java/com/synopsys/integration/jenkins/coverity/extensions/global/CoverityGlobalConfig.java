/*
 * synopsys-coverity
 *
 * Copyright (c) 2023 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
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

    @DataBoundConstructor
    public CoverityGlobalConfig() {
        load();
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
