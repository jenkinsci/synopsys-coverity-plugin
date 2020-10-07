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
package com.synopsys.integration.jenkins.coverity.extensions;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.ChangeSetFilter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;

public class ConfigureChangeSetPatterns extends AbstractDescribableImpl<ConfigureChangeSetPatterns> {
    @HelpMarkdown("Specify a comma separated list of filename patterns that you would like to explicitly excluded from the Jenkins change set.  \r\n"
                      + "The pattern is applied to the $CHANGE_SET environment variable, and will affect which files are analyzed in an incremental analysis (cov-run-desktop).  \r\n"
                      + "Examples:\r\n"
                      + "\r\n"
                      + "| File Name | Pattern    | Will be excluded |\r\n"
                      + "| --------- | ---------- | ---------------- |\r\n"
                      + "| test.java | *.java     | Yes              |\r\n"
                      + "| test.java | *.jpg      | No               |\r\n"
                      + "| test.java | test.*     | Yes              |\r\n"
                      + "| test.java | test.????  | Yes              |\r\n"
                      + "| test.java | test.????? | No               |")
    private final String changeSetExclusionPatterns;

    @HelpMarkdown("Specify a comma separated list of filename patterns that you would like to explicitly included from the Jenkins change set.  \r\n"
                      + "The pattern is applied to the $CHANGE_SET environment variable, and will affect which files are analyzed in an incremental analysis (cov-run-desktop).  \r\n"
                      + "Examples:\r\n"
                      + "\r\n"
                      + "| File Name | Pattern    | Will be included |\r\n"
                      + "| --------- | ---------- | ---------------- |\r\n"
                      + "| test.java | *.java     | Yes              |\r\n"
                      + "| test.java | *.jpg      | No               |\r\n"
                      + "| test.java | test.*     | Yes              |\r\n"
                      + "| test.java | test.????  | Yes              |\r\n"
                      + "| test.java | test.????? | No               |")
    private final String changeSetInclusionPatterns;

    @DataBoundConstructor
    public ConfigureChangeSetPatterns(String changeSetExclusionPatterns, String changeSetInclusionPatterns) {
        this.changeSetExclusionPatterns = changeSetExclusionPatterns;
        this.changeSetInclusionPatterns = changeSetInclusionPatterns;
    }

    public String getChangeSetInclusionPatterns() {
        return changeSetInclusionPatterns;
    }

    public String getChangeSetExclusionPatterns() {
        return changeSetExclusionPatterns;
    }

    public ChangeSetFilter createChangeSetFilter() {
        return new ChangeSetFilter(changeSetExclusionPatterns, changeSetInclusionPatterns);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ConfigureChangeSetPatterns> {
        public DescriptorImpl() {
            super(ConfigureChangeSetPatterns.class);
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

    }

}
