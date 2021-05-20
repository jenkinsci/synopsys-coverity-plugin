/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class AdvancedCoverityRunConfiguration extends CoverityRunConfiguration {
    private final RepeatableCommand[] commands;

    @DataBoundConstructor
    public AdvancedCoverityRunConfiguration(final RepeatableCommand[] commands) {
        this.commands = commands;
    }

    public RepeatableCommand[] getCommands() {
        return commands;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public RunConfigurationType getRunConFigurationType() {
        return RunConfigurationType.ADVANCED;
    }

    @Extension
    public static class DescriptorImpl extends CoverityRunConfiguration.RunConfigurationDescriptor {
        public DescriptorImpl() {
            super(AdvancedCoverityRunConfiguration.class);
            load();
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return RunConfigurationType.ADVANCED.getDisplayName();
        }
    }

}
