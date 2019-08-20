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
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.coverity.executable.SynopsysEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

public class RepeatableCommand extends AbstractDescribableImpl<RepeatableCommand> {
    private static final String JENKINS_INTERMEDIATE_DIRECTORY = "${WORKSPACE}/idir";
    private final String command;

    @DataBoundConstructor
    public RepeatableCommand(final String command) {
        this.command = command;
    }

    public static RepeatableCommand COV_BUILD(final String buildCommand, final String arguments) {
        final List<String> commandPieces = Arrays.asList("cov-build",
            Argument.DIR.toString(), JENKINS_INTERMEDIATE_DIRECTORY,
            arguments,
            buildCommand);

        return constructCommand(commandPieces);
    }

    public static RepeatableCommand COV_ANALYZE(final String arguments) {
        final List<String> commandPieces = Arrays.asList("cov-analyze",
            Argument.DIR.toString(), JENKINS_INTERMEDIATE_DIRECTORY,
            arguments);

        return constructCommand(commandPieces);
    }

    public static RepeatableCommand COV_RUN_DESKTOP(final String arguments, final String filePaths) {
        final List<String> commandPieces = Arrays.asList("cov-run-desktop",
            Argument.DIR.toString(), JENKINS_INTERMEDIATE_DIRECTORY,
            Argument.URL.toString(), generateExpansionString(JenkinsCoverityEnvironmentVariable.COVERITY_URL),
            Argument.STREAM.toString(), generateExpansionString(JenkinsCoverityEnvironmentVariable.COVERITY_STREAM),
            arguments,
            filePaths);

        return constructCommand(commandPieces);
    }

    public static RepeatableCommand COV_COMMIT_DEFECTS(final String arguments) {
        final List<String> commandPieces = Arrays.asList("cov-commit-defects",
            Argument.DIR.toString(), JENKINS_INTERMEDIATE_DIRECTORY,
            Argument.URL.toString(), generateExpansionString(JenkinsCoverityEnvironmentVariable.COVERITY_URL),
            Argument.STREAM.toString(), generateExpansionString(JenkinsCoverityEnvironmentVariable.COVERITY_STREAM),
            arguments);

        return constructCommand(commandPieces);
    }

    public static RepeatableCommand COV_CAPTURE_PROJECT(final String projectDir, final String arguments) {
        final List<String> commandPieces = Arrays.asList("cov-capture",
            Argument.PROJECT_DIR.toString(), projectDir,
            Argument.DIR.toString(), JENKINS_INTERMEDIATE_DIRECTORY,
            arguments
        );

        return constructCommand(commandPieces);
    }

    public static RepeatableCommand COV_CAPTURE_SCM(final String scmUrl, final String arguments) {
        final List<String> commandPieces = Arrays.asList("cov-capture",
            Argument.SCM_URL.toString(), scmUrl,
            Argument.DIR.toString(), JENKINS_INTERMEDIATE_DIRECTORY,
            arguments
        );

        return constructCommand(commandPieces);
    }

    private static RepeatableCommand constructCommand(final List<String> commandPieces) {
        return new RepeatableCommand(String.join(" ", commandPieces));
    }

    private static String generateExpansionString(final SynopsysEnvironmentVariable environmentVariable) {
        return String.format("${%s}", environmentVariable.toString());
    }

    public String getCommand() {
        return command;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public enum Argument {
        DIR("--dir"),
        PROJECT_DIR("--project-dir"),
        URL("--url"),
        SCM_URL("--scm-url"),
        STREAM("--stream");

        private final String text;

        Argument(final String text) {
            this.text = text;
        }

        public String toString() {
            return this.text;
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RepeatableCommand> {
        public DescriptorImpl() {
            super(RepeatableCommand.class);
            load();
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "";
        }

        public FormValidation doCheckCommand(@QueryParameter("command") final String command) {
            if (StringUtils.isBlank(command)) {
                return FormValidation.error("The Coverity command can not be empty");
            }
            return FormValidation.ok();
        }
    }

}
