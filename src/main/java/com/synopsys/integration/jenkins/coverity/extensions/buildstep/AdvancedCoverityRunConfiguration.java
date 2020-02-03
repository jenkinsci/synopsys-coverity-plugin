/**
 * synopsys-coverity
 *
 * Copyright (c) 2020 Synopsys, Inc.
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
