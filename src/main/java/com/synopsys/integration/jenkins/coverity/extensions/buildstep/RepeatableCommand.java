/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.CHANGE_SET;
import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.COVERITY_INTERMEDIATE_DIRECTORY;
import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.COVERITY_STREAM;
import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.COVERITY_URL;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.jenkins.annotations.HelpMarkdown;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

public class RepeatableCommand extends AbstractDescribableImpl<RepeatableCommand> {
    @HelpMarkdown("Provide the Coverity command you want to run.  \r\n"
                      + "The command should start with the name of the Coverity command you want to run. Ex: cov-build, cov-analyze, etc.  \r\n"
                      + "For examples and a list of the available environment variables that can be used, refer to [the Command Examples documentation](https://synopsys.atlassian.net/wiki/spaces/INTDOCS/pages/623024/Coverity+Command+Examples)")
    private final String command;

    @DataBoundConstructor
    public RepeatableCommand(String command) {
        this.command = command;
    }

    public static RepeatableCommand COV_BUILD(String buildCommand, String arguments) {
        List<String> commandPieces = new ArrayList<>();
        commandPieces.add("cov-build");
        commandPieces.add(Argument.DIR.toString());
        commandPieces.add(COVERITY_INTERMEDIATE_DIRECTORY.expansionString());

        if (StringUtils.isNotBlank(arguments)) {
            commandPieces.add(arguments);
        }

        if (StringUtils.isNotBlank(buildCommand)) {
            commandPieces.add(buildCommand);
        }

        return constructCommand(commandPieces);
    }

    public static RepeatableCommand COV_ANALYZE(String arguments) {
        List<String> commandPieces = new ArrayList<>();
        commandPieces.add("cov-analyze");
        commandPieces.add(Argument.DIR.toString());
        commandPieces.add(COVERITY_INTERMEDIATE_DIRECTORY.expansionString());

        if (StringUtils.isNotBlank(arguments)) {
            commandPieces.add(arguments);
        }

        return constructCommand(commandPieces);
    }

    public static RepeatableCommand COV_RUN_DESKTOP(String authKeyFilePath, String arguments) {
        List<String> commandPieces = new ArrayList<>();
        commandPieces.add("cov-run-desktop");
        commandPieces.add(Argument.DIR.toString());
        commandPieces.add(COVERITY_INTERMEDIATE_DIRECTORY.expansionString());
        commandPieces.add(Argument.URL.toString());
        commandPieces.add(COVERITY_URL.expansionString());
        commandPieces.add(Argument.STREAM.toString());
        commandPieces.add(COVERITY_STREAM.expansionString());

        if (StringUtils.isNotBlank(authKeyFilePath)) {
            commandPieces.add(Argument.AUTH_KEY_FILE.toString());
            commandPieces.add(authKeyFilePath);
        }

        if (StringUtils.isNotBlank(arguments)) {
            commandPieces.add(arguments);
        }

        commandPieces.add(CHANGE_SET.expansionString());

        return constructCommand(commandPieces);
    }

    public static RepeatableCommand COV_COMMIT_DEFECTS(String authKeyFilePath, String arguments) {
        List<String> commandPieces = new ArrayList<>();
        commandPieces.add("cov-commit-defects");
        commandPieces.add(Argument.DIR.toString());
        commandPieces.add(COVERITY_INTERMEDIATE_DIRECTORY.expansionString());
        commandPieces.add(Argument.URL.toString());
        commandPieces.add(COVERITY_URL.expansionString());
        commandPieces.add(Argument.STREAM.toString());
        commandPieces.add(COVERITY_STREAM.expansionString());

        if (StringUtils.isNotBlank(authKeyFilePath)) {
            commandPieces.add(Argument.AUTH_KEY_FILE.toString());
            commandPieces.add(authKeyFilePath);
        }

        if (StringUtils.isNotBlank(arguments)) {
            commandPieces.add(arguments);
        }

        return constructCommand(commandPieces);
    }

    public static RepeatableCommand COV_CAPTURE_PROJECT(String projectDir, String arguments) {
        List<String> commandPieces = new ArrayList<>();
        commandPieces.add("cov-capture");

        if (StringUtils.isNotBlank(projectDir)) {
            commandPieces.add(Argument.PROJECT_DIR.toString());
            commandPieces.add(projectDir);
        }

        commandPieces.add(Argument.DIR.toString());
        commandPieces.add(COVERITY_INTERMEDIATE_DIRECTORY.expansionString());

        if (StringUtils.isNotBlank(arguments)) {
            commandPieces.add(arguments);
        }

        return constructCommand(commandPieces);
    }

    public static RepeatableCommand COV_CAPTURE_SCM(String scmUrl, String arguments) {
        List<String> commandPieces = new ArrayList<>();
        commandPieces.add("cov-capture");

        if (StringUtils.isNotBlank(scmUrl)) {
            commandPieces.add(Argument.SCM_URL.toString());
            commandPieces.add(scmUrl);
        }

        commandPieces.add(Argument.DIR.toString());
        commandPieces.add(COVERITY_INTERMEDIATE_DIRECTORY.expansionString());

        if (StringUtils.isNotBlank(arguments)) {
            commandPieces.add(arguments);
        }

        return constructCommand(commandPieces);
    }

    private static RepeatableCommand constructCommand(List<String> commandPieces) {
        String command = commandPieces.stream()
                             .filter(StringUtils::isNotBlank)
                             .collect(Collectors.joining(" "));

        return new RepeatableCommand(command);
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
        STREAM("--stream"),
        AUTH_KEY_FILE("--auth-key-file");

        private final String text;

        Argument(String text) {
            this.text = text;
        }

        @Override
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

        public FormValidation doCheckCommand(@QueryParameter("command") String command) {
            if (StringUtils.isBlank(command)) {
                return FormValidation.error("The Coverity command can not be empty");
            }
            return FormValidation.ok();
        }
    }

}
