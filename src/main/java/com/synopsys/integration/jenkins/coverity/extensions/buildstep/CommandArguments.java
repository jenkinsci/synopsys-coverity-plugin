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

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

public class CommandArguments extends AbstractDescribableImpl<CommandArguments> {
    private final String covBuildArguments;
    private final String covAnalyzeArguments;
    private final String covRunDesktopArguments;
    private final String covCommitDefectsArguments;
    private final String covCaptureArguments;

    @DataBoundConstructor
    public CommandArguments(final String covBuildArguments, final String covAnalyzeArguments, final String covRunDesktopArguments, final String covCommitDefectsArguments, final String covCaptureArguments) {
        this.covBuildArguments = covBuildArguments;
        this.covAnalyzeArguments = covAnalyzeArguments;
        this.covRunDesktopArguments = covRunDesktopArguments;
        this.covCommitDefectsArguments = covCommitDefectsArguments;
        this.covCaptureArguments = covCaptureArguments;
    }

    public String getCovBuildArguments() {
        return covBuildArguments;
    }

    public String getCovAnalyzeArguments() {
        return covAnalyzeArguments;
    }

    public String getCovRunDesktopArguments() {
        return covRunDesktopArguments;
    }

    public String getCovCommitDefectsArguments() {
        return covCommitDefectsArguments;
    }

    public String getCovCaptureArguments() {
        return covCaptureArguments;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CommandArguments> {
        public DescriptorImpl() {
            super(CommandArguments.class);
            load();
        }

        public FormValidation doCheckCovBuildArguments(@QueryParameter("covBuildArguments") final String covBuildArguments) {
            return checkForAlreadyProvidedArguments(covBuildArguments, RepeatableCommand.Argument.DIR);
        }

        public FormValidation doCheckCovAnalyzeArguments(@QueryParameter("covAnalyzeArguments") final String covAnalyzeArguments) {
            return checkForAlreadyProvidedArguments(covAnalyzeArguments, RepeatableCommand.Argument.DIR);
        }

        public FormValidation doCheckCovRunDesktopArguments(@QueryParameter("covRunDesktopArguments") final String covRunDesktopArguments) {
            return checkForAlreadyProvidedArguments(covRunDesktopArguments, RepeatableCommand.Argument.DIR, RepeatableCommand.Argument.URL, RepeatableCommand.Argument.STREAM);
        }

        public FormValidation doCheckCovCommitDefectsArguments(@QueryParameter("covCommitDefectsArguments") final String covCommitDefectsArguments) {
            return checkForAlreadyProvidedArguments(covCommitDefectsArguments, RepeatableCommand.Argument.DIR, RepeatableCommand.Argument.URL, RepeatableCommand.Argument.STREAM);
        }

        private FormValidation checkForAlreadyProvidedArguments(final String command, final RepeatableCommand.Argument... providedArguments) {
            final String alreadyProvidedArguments = Arrays.stream(providedArguments)
                                                        .map(RepeatableCommand.Argument::toString)
                                                        .filter(command::contains)
                                                        .collect(Collectors.joining(", "));

            if (StringUtils.isNotBlank(alreadyProvidedArguments)) {
                return FormValidation.error(String.format("The argument(s) %s are automatically provided in this mode. If you wish to override, configure the 'Run custom Coverity commands' section instead.", alreadyProvidedArguments));
            }
            return FormValidation.ok();
        }

    }

}
