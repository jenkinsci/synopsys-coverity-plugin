/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public abstract class CoverityRunConfiguration extends AbstractDescribableImpl<CoverityRunConfiguration> {
    public abstract RunConfigurationType getRunConFigurationType();

    @Override
    public RunConfigurationDescriptor getDescriptor() {
        return (RunConfigurationDescriptor) super.getDescriptor();
    }

    public enum RunConfigurationType implements JenkinsSelectBoxEnum {
        SIMPLE("Run default Coverity workflow"),
        ADVANCED("Run custom Coverity commands");

        private final String displayName;

        RunConfigurationType(final String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }
    }

    public static abstract class RunConfigurationDescriptor extends Descriptor<CoverityRunConfiguration> {
        public RunConfigurationDescriptor(final Class<? extends CoverityRunConfiguration> clazz) {
            super(clazz);
        }
    }

}
