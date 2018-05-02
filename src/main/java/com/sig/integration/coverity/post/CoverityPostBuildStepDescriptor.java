/**
 * sig-coverity
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
package com.sig.integration.coverity.post;

import java.io.Serializable;

import org.kohsuke.stapler.StaplerRequest;

import com.sig.integration.coverity.CoverityInstance;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;

@Extension()
public class CoverityPostBuildStepDescriptor extends BuildStepDescriptor<Publisher> implements Serializable {
    private CoverityInstance coverityInstance;

    public CoverityPostBuildStepDescriptor() {
        super(CoverityPostBuildStep.class);
        load();
    }

    public CoverityInstance getCoverityInstance() {
        return coverityInstance;
    }

    @Override
    public String getDisplayName() {
        return "Sig Coverity";
    }

    @Override
    public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
        return true;
    }

    @Override
    public boolean configure(final StaplerRequest req, final JSONObject formData) throws Descriptor.FormException {
        String url = formData.getString("url");
        coverityInstance = new CoverityInstance(url);
        save();
        return super.configure(req, formData);
    }
}
