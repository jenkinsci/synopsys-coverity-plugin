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
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import java.io.Serializable;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValueProvider;

import hudson.Extension;
import hudson.util.ListBoxModel;

public class SimpleCoverityRunConfiguration extends CoverityRunConfiguration implements Serializable {
    private static final long serialVersionUID = -2347823487327823489L;

    private final CoverityAnalysisType coverityAnalysisType;
    private final String buildCommand;
    private final CommandArguments commandArguments;

    @DataBoundConstructor
    public SimpleCoverityRunConfiguration(final CoverityAnalysisType coverityAnalysisType, final String buildCommand, final CommandArguments commandArguments) {
        this.coverityAnalysisType = coverityAnalysisType;
        this.buildCommand = buildCommand;
        this.commandArguments = commandArguments;
    }

    public CoverityAnalysisType getCoverityAnalysisType() {
        return coverityAnalysisType;
    }

    public String getBuildCommand() {
        return buildCommand;
    }

    public CommandArguments getCommandArguments() {
        return commandArguments;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public RunConfigurationType getRunConFigurationType() {
        return RunConfigurationType.SIMPLE;
    }

    @Extension
    public static class DescriptorImpl extends CoverityRunConfiguration.DescriptorImpl {
        public DescriptorImpl() {
            super(SimpleCoverityRunConfiguration.class);
            load();
        }

        public ListBoxModel doFillCoverityAnalysisTypeItems() {
            return CommonFieldValueProvider.getListBoxModelOf(CoverityAnalysisType.values());
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return RunConfigurationType.SIMPLE.getDisplayName();
        }
    }

}
