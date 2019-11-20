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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.synopsys.integration.jenkins.HelpMarkdown;
import com.synopsys.integration.jenkins.SerializationHelper;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityCaptureType;
import com.synopsys.integration.jenkins.coverity.extensions.utils.FieldHelper;

import hudson.Extension;
import hudson.util.ListBoxModel;

public class SimpleCoverityRunConfiguration extends CoverityRunConfiguration {
    static {
        // TODO: Migrated in 2.1.0 -- Remove migration in 3.0.0
        SerializationHelper.migrateFieldFrom("buildCommand", SimpleCoverityRunConfiguration.class, "sourceArgument");
    }

    private final CommandArguments commandArguments;

    @HelpMarkdown("Specify the way you wish to perform your Coverity analysis.  \r\n"
                      + "Each analysis type runs a specific Coverity command with some default arguments, followed by cov-commit-defects with some default arguments. Additional command-specific arguments can be provided below.\r\n"
                      + "\r\n"
                      + "**Full Analysis**:  \r\n"
                      + "cov-analyze --dir ${WORKSPACE}/idir  \r\n"
                      + "cov-commit-defects --dir ${WORKSPACE}/idir --url ${COV_URL} --stream ${COV_STREAM}\r\n"
                      + "\r\n"
                      + "**Incremental Analysis**:  \r\n"
                      + "cov-run-desktop --dir ${WORKSPACE}/idir --url ${COV_URL} --stream ${COV_STREAM} ${CHANGE_SET}  \r\n"
                      + "cov-commit-defects --dir ${WORKSPACE}/idir --url ${COV_URL} --stream ${COV_STREAM}\r\n"
                      + "\r\n"
                      + "**Determined by change set threshold**  \r\n"
                      + "Will run the commands specified by **Full Analysis** if the number of files listed in the CHANGE_SET environment variable meets or exceeds the specified threshold, otherwise will run the commands specified by **Incremental Analysis**.")
    private final CoverityAnalysisType coverityAnalysisType;

    @HelpMarkdown("The argument that specifies the source for the given capture type.  \r\n"
                      + "For **Build**, this is the build command to pass to cov-build.  \r\n"
                      + "For **Buildless Capture (Project)** this is the project directory to pass to cov-capture.  \r\n"
                      + "For **Buildless Capture (SCM)** this is the scm url to pass to cov-capture.")
    private final String sourceArgument;

    // Any field set by a DataBoundSetter should be explicitly declared as @Nullable to avoid accidental NPEs -- rotte 11/13/2019
    @Nullable
    @HelpMarkdown("The fully qualified path to a directory in which to run the cov-build or cov-capture command and create the idir.")
    private String customWorkingDirectory;

    @Nullable
    @HelpMarkdown("Specify the way you wish to capture your source for Coverity analysis.  \r\n"
                      + "Each capture type runs a specific Coverity command  with some default arguments. Additional command-specific arguments can be provided below.\r\n"
                      + "\r\n"
                      + "**Build**  \r\n"
                      + "cov-build --dir ${WORKSPACE}/idir *Source Argument (build command)*\r\n"
                      + "\r\n"
                      + "**Buildless Capture (Project)**  \r\n"
                      + "cov-capture --project-dir *Source Argument (project directory)*\r\n"
                      + "\r\n"
                      + "**Buildless Capture (SCM)**  \r\n"
                      + "cov-capture --scm-url *Source Argument (scm url)*")
    private CoverityCaptureType coverityCaptureType;

    @Nullable
    @HelpMarkdown("For use with the Coverity Analysis Type **Determined by change set threshold**. Specifies the number of files that triggers a **Full Analysis**.\r\n"
                      + "\r\n"
                      + "**Determined by change set threshold** will run an **Incremental Analysis** unless the number of files specified in the $CHANGE_SET environment variable meets or exceeds the value of this field.  \r\n"
                      + "If the number of files specified in the $CHANGE_SET environment variable meets or exceeds the value of this field, **Determined by change set threshold** will run a **Full Analysis**")
    private Integer changeSetAnalysisThreshold;

    @DataBoundConstructor
    public SimpleCoverityRunConfiguration(final CoverityAnalysisType coverityAnalysisType, final String sourceArgument, final CommandArguments commandArguments) {
        this.coverityAnalysisType = coverityAnalysisType;
        this.sourceArgument = sourceArgument;
        this.commandArguments = commandArguments;
    }

    public static SimpleCoverityRunConfiguration DEFAULT_CONFIGURATION() {
        final SimpleCoverityRunConfiguration defaultCoverityRunConfiguration = new SimpleCoverityRunConfiguration(CoverityAnalysisType.COV_ANALYZE, "", null);
        defaultCoverityRunConfiguration.setCoverityCaptureType(CoverityCaptureType.COV_BUILD);
        defaultCoverityRunConfiguration.setChangeSetAnalysisThreshold(100);
        return defaultCoverityRunConfiguration;
    }

    public String getSourceArgument() {
        return sourceArgument;
    }

    public CommandArguments getCommandArguments() {
        return commandArguments;
    }

    public CoverityAnalysisType getCoverityAnalysisType() {
        return coverityAnalysisType;
    }

    public CoverityAnalysisType getDefaultCoverityAnalysisType() {
        return CoverityAnalysisType.COV_ANALYZE;
    }

    public int getChangeSetAnalysisThreshold() {
        if (changeSetAnalysisThreshold == null) {
            return 0;
        }
        return changeSetAnalysisThreshold;
    }

    @DataBoundSetter
    public void setChangeSetAnalysisThreshold(final Integer changeSetAnalysisThreshold) {
        this.changeSetAnalysisThreshold = changeSetAnalysisThreshold;
    }

    public String getCustomWorkingDirectory() {
        return customWorkingDirectory;
    }

    @DataBoundSetter
    public void setCustomWorkingDirectory(final String customWorkingDirectory) {
        this.customWorkingDirectory = customWorkingDirectory;
    }

    public CoverityCaptureType getCoverityCaptureType() {
        return coverityCaptureType;
    }

    @DataBoundSetter
    public void setCoverityCaptureType(final CoverityCaptureType coverityCaptureType) {
        this.coverityCaptureType = coverityCaptureType;
    }

    public CoverityCaptureType getDefaultCoverityCaptureType() {
        return CoverityCaptureType.COV_BUILD;
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
    public static class DescriptorImpl extends CoverityRunConfiguration.RunConfigurationDescriptor {
        public DescriptorImpl() {
            super(SimpleCoverityRunConfiguration.class);
            load();
        }

        public ListBoxModel doFillCoverityCaptureTypeItems() {
            return FieldHelper.getListBoxModelOf(CoverityCaptureType.values());
        }

        public ListBoxModel doFillCoverityAnalysisTypeItems() {
            return FieldHelper.getListBoxModelOf(CoverityAnalysisType.values());
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return RunConfigurationType.SIMPLE.getDisplayName();
        }
    }

}
