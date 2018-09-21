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

package com.synopsys.integration.coverity.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import com.synopsys.integration.coverity.CoverityVersion;
import com.synopsys.integration.coverity.Messages;
import com.synopsys.integration.coverity.freestyle.CoverityPostBuildStepDescriptor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * Coverity Static Analysis Tool {@link ToolInstallation}, this represents a named tool configuration with a path to
 * the install directory.
 */
public class CoverityToolInstallation extends ToolInstallation implements NodeSpecific<CoverityToolInstallation>, EnvironmentSpecific<CoverityToolInstallation> {
    @DataBoundConstructor
    public CoverityToolInstallation(String name, String home) {
        super(name, home, new DescribableList<ToolProperty<?>, ToolPropertyDescriptor>(Saveable.NOOP));
    }

    @Override
    public CoverityToolInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new CoverityToolInstallation(getName(), translateFor(node, log));
    }

    @Override
    public CoverityToolInstallation forEnvironment(EnvVars environment) {
        return new CoverityToolInstallation(getName(), environment.expand(getHome()));
    }

    /**
     * \
     * {@link ToolDescriptor} for {@link CoverityToolInstallation}
     */
    @Extension
    @Symbol("coverity")
    public static final class CoverityToolInstallationDescriptor extends ToolDescriptor<CoverityToolInstallation> {

        @Override
        public String getDisplayName() {
            return Messages.CoverityToolInstallation_getDisplayName();
        }

        /**
         * Override in order to remove the default "install automatically" checkbox for tool installation
         */
        @Override
        public List<ToolPropertyDescriptor> getPropertyDescriptors() {
            return Collections.emptyList();
        }

        @Override
        public DescribableList<ToolProperty<?>, ToolPropertyDescriptor> getDefaultProperties() {
            return new DescribableList<>(NOOP);
        }

        @Override
        public CoverityToolInstallation[] getInstallations() {
            return getCoverityPostBuildStepDescriptor().getCoverityToolInstallations();
        }

        @Override
        public void setInstallations(CoverityToolInstallation... installations) {
            getCoverityPostBuildStepDescriptor().setCoverityToolInstallations(installations);
        }

        private CoverityPostBuildStepDescriptor getCoverityPostBuildStepDescriptor() {
            return Jenkins.getInstance().getDescriptorByType(CoverityPostBuildStepDescriptor.class);
        }

        @Override
        protected FormValidation checkHomeDirectory(File home) {
            // This validation is only ever run when on master. Jenkins does not use this to validate node overrides
            try {
                File analysisVersionXml = new File(home, "VERSION.xml");
                if (home != null && home.exists()) {
                    if (analysisVersionXml.isFile()) {

                        // check the version file value and validate it is greater than minimum version
                        Optional<CoverityVersion> optionalVersion = getVersion(home);

                        if (!optionalVersion.isPresent()) {
                            return FormValidation.error("Could not determine the version of the Coverity analysis tool.");
                        }
                        CoverityVersion version = optionalVersion.get();
                        if (version.compareTo(CoverityPostBuildStepDescriptor.MINIMUM_SUPPORTED_VERSION) < 0) {
                            return FormValidation.error("Analysis version " + version.toString() + " detected. " +
                                                            "The minimum supported version is " + CoverityPostBuildStepDescriptor.MINIMUM_SUPPORTED_VERSION.toString());
                        }

                        return FormValidation.ok("Analysis installation directory has been verified.");
                    } else {
                        return FormValidation.error("The specified Analysis installation directory doesn't contain a VERSION.xml file.");
                    }
                } else {
                    return FormValidation.error("The specified Analysis installation directory doesn't exists.");
                }
            } catch (IOException e) {
                return FormValidation.error("Unable to verify the Analysis installation directory.");
            }
        }

        /*
         * Gets the {@link CoverityVersion} given a static analysis tools home directory by finding the VERSION file,
         * then reading the version number
         */
        public Optional<CoverityVersion> getVersion(File home) throws IOException {
            File versionFile = new File(home, "VERSION.xml");
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(versionFile), StandardCharsets.UTF_8))) {
                final String prefix = "externalVersion=";
                String line, version = "";
                while ((line = br.readLine()) != null) {
                    if (line.startsWith(prefix)) {
                        version = line.substring(prefix.length(), line.length());
                        break;
                    }
                }
                return CoverityVersion.parse(version);
            }
        }
    }
}
