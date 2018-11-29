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

import java.net.URL;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

public class RepeatableCommand extends AbstractDescribableImpl<RepeatableCommand> {
    private static final String JENKINS_INTERMEDIATE_DIRECTORY = "--dir ${WORKSPACE}/idir";
    private static final String CIM_INSTANCE_HOST = "--host ${COVERITY_HOST}";
    private static final String CIM_INSTANCE_PORT = "--port ${COVERITY_PORT}";
    private static final String COVERITY_STREAM = "--stream ${COV_STREAM}";
    private static final String SSL_OPTION = "--ssl";
    private final String command;

    @DataBoundConstructor
    public RepeatableCommand(final String command) {
        this.command = command;
    }

    public static RepeatableCommand DEFAULT_COV_ANALYZE() {
        return new RepeatableCommand("cov-analyze " + JENKINS_INTERMEDIATE_DIRECTORY);
    }

    public static RepeatableCommand DEFAULT_COV_COMMIT_DEFECTS(final URL url) {
        String covCommitDefects = String.format("cov-commit-defects %s %s", JENKINS_INTERMEDIATE_DIRECTORY, COVERITY_STREAM);
        covCommitDefects = handleConnectionOptions(url, covCommitDefects);
        return new RepeatableCommand(covCommitDefects);
    }

    public static RepeatableCommand DEFAULT_COV_RUN_DESKTOP(final URL url, final String filePaths) {
        String covRunDesktop = String.format("cov-run-desktop %s %s", JENKINS_INTERMEDIATE_DIRECTORY, COVERITY_STREAM);
        covRunDesktop = handleConnectionOptions(url, covRunDesktop);
        covRunDesktop = String.format("%s %s", covRunDesktop, filePaths);
        return new RepeatableCommand(covRunDesktop);
    }

    public static RepeatableCommand DEFAULT_COV_BUILD(final String buildCommand) {
        return new RepeatableCommand(String.format("cov-build %s %s", JENKINS_INTERMEDIATE_DIRECTORY, buildCommand));
    }

    private static String handleConnectionOptions(final URL url, final String initialCommand) {
        String newCommand = String.format("%s %s", initialCommand, CIM_INSTANCE_HOST);
        if (url != null) {
            if (-1 != url.getPort()) {
                newCommand = String.format("%s %s", newCommand, CIM_INSTANCE_PORT);
            }
            if ("https".equals(url.getProtocol())) {
                newCommand = String.format("%s %s", newCommand, SSL_OPTION);
            }
        }
        return newCommand;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public RepeatableCommandDescriptor getDescriptor() {
        return (RepeatableCommandDescriptor) super.getDescriptor();
    }

    @Extension
    public static class RepeatableCommandDescriptor extends Descriptor<RepeatableCommand> {
        public RepeatableCommandDescriptor() {
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
