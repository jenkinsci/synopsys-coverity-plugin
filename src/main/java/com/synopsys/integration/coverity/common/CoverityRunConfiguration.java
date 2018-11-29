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

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public class CoverityRunConfiguration extends AbstractDescribableImpl<CoverityRunConfiguration> {
    private RepeatableCommand[] commands;
    private CoverityAnalysisType coverityAnalysisType;
    private String buildCommand;
    private Boolean commandArguments;
    private String covBuildArguments;
    private String covAnalyzeArguments;
    private String covRunDesktopArguments;
    private String covCommitDefectsArguments;

    @DataBoundConstructor
    public CoverityRunConfiguration() {
    }

    public RepeatableCommand[] getCommands() {
        return commands;
    }

    @DataBoundSetter
    public void setCommands(final RepeatableCommand[] commands) {
        this.commands = commands;
    }

    public CoverityAnalysisType getCoverityAnalysisType() {
        return coverityAnalysisType;
    }

    @DataBoundSetter
    public void setCoverityAnalysisType(final CoverityAnalysisType coverityAnalysisType) {
        this.coverityAnalysisType = coverityAnalysisType;
    }

    public String getBuildCommand() {
        return buildCommand;
    }

    @DataBoundSetter
    public void setBuildCommand(final String buildCommand) {
        this.buildCommand = buildCommand;
    }

    public Boolean getCommandArguments() {
        return commandArguments;
    }

    @DataBoundSetter
    public void setCommandArguments(final Boolean commandArguments) {
        this.commandArguments = commandArguments;
    }

    public String getCovBuildArguments() {
        return covBuildArguments;
    }

    @DataBoundSetter
    public void setCovBuildArguments(final String covBuildArguments) {
        this.covBuildArguments = covBuildArguments;
    }

    public String getCovAnalyzeArguments() {
        return covAnalyzeArguments;
    }

    @DataBoundSetter
    public void setCovAnalyzeArguments(final String covAnalyzeArguments) {
        this.covAnalyzeArguments = covAnalyzeArguments;
    }

    public String getCovRunDesktopArguments() {
        return covRunDesktopArguments;
    }

    @DataBoundSetter
    public void setCovRunDesktopArguments(final String covRunDesktopArguments) {
        this.covRunDesktopArguments = covRunDesktopArguments;
    }

    public String getCovCommitDefectsArguments() {
        return covCommitDefectsArguments;
    }

    @DataBoundSetter
    public void setCovCommitDefectsArguments(final String covCommitDefectsArguments) {
        this.covCommitDefectsArguments = covCommitDefectsArguments;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public enum Value implements CoveritySelectBoxEnum {
        SIMPLE("Run Coverity build, analyze, and commit defects"),
        ADVANCED("Run custom Coverity commands");

        private final String displayName;

        Value(final String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }
    }

    private class DescriptorImpl extends Descriptor<CoverityRunConfiguration> {
        public DescriptorImpl() {
            super(CoverityRunConfiguration.class);
            load();
        }
    }

}
