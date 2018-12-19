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
package com.synopsys.integration.jenkins.coverity.buildstep;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;

public class ConfigureChangeSetPatterns extends AbstractDescribableImpl<ConfigureChangeSetPatterns> implements Serializable {
    private static final long serialVersionUID = 2552665058566675304L;
    private final String changeSetExclusionPatterns;
    private final String changeSetInclusionPatterns;

    @DataBoundConstructor
    public ConfigureChangeSetPatterns(final String changeSetExclusionPatterns, final String changeSetInclusionPatterns) {
        this.changeSetExclusionPatterns = changeSetExclusionPatterns;
        this.changeSetInclusionPatterns = changeSetInclusionPatterns;
    }

    public String getChangeSetInclusionPatterns() {
        return changeSetInclusionPatterns;
    }

    public String getChangeSetExclusionPatterns() {
        return changeSetExclusionPatterns;
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
        public boolean configure(final StaplerRequest req, final JSONObject formData) throws Descriptor.FormException {
            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

    }

}
