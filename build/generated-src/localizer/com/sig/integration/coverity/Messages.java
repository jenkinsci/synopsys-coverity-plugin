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
// CHECKSTYLE:OFF

package com.sig.integration.coverity;

import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;

@SuppressWarnings({
    "",
    "PMD"
})
public class Messages {

    private final static ResourceBundleHolder holder = ResourceBundleHolder.get(Messages.class);

    /**
     * Sig Coverity
     * 
     */
    public static String CoverityPipelineStep_getDisplayName() {
        return holder.format("CoverityPipelineStep_getDisplayName");
    }

    /**
     * Sig Coverity
     * 
     */
    public static Localizable _CoverityPipelineStep_getDisplayName() {
        return new Localizable(holder, "CoverityPipelineStep_getDisplayName");
    }

    /**
     * Coverity Static Analysis Tools
     * 
     */
    public static String CoverityToolInstallation_getDisplayName() {
        return holder.format("CoverityToolInstallation_getDisplayName");
    }

    /**
     * Coverity Static Analysis Tools
     * 
     */
    public static Localizable _CoverityToolInstallation_getDisplayName() {
        return new Localizable(holder, "CoverityToolInstallation_getDisplayName");
    }

    /**
     * Please specify the credentials for the Coverity server.
     * 
     */
    public static String CoverityPostBuildStep_getPleaseSetCoverityCredentials() {
        return holder.format("CoverityPostBuildStep_getPleaseSetCoverityCredentials");
    }

    /**
     * Please specify the credentials for the Coverity server.
     * 
     */
    public static Localizable _CoverityPostBuildStep_getPleaseSetCoverityCredentials() {
        return new Localizable(holder, "CoverityPostBuildStep_getPleaseSetCoverityCredentials");
    }

    /**
     * Sig Coverity
     * 
     */
    public static String CoverityPostBuildStep_getDisplayName() {
        return holder.format("CoverityPostBuildStep_getDisplayName");
    }

    /**
     * Sig Coverity
     * 
     */
    public static Localizable _CoverityPostBuildStep_getDisplayName() {
        return new Localizable(holder, "CoverityPostBuildStep_getDisplayName");
    }

    /**
     * Please provide a URL for the Coverity server.
     * 
     */
    public static String CoverityPostBuildStep_getPleaseSetServerUrl() {
        return holder.format("CoverityPostBuildStep_getPleaseSetServerUrl");
    }

    /**
     * Please provide a URL for the Coverity server.
     * 
     */
    public static Localizable _CoverityPostBuildStep_getPleaseSetServerUrl() {
        return new Localizable(holder, "CoverityPostBuildStep_getPleaseSetServerUrl");
    }

    /**
     * The Coverity URL provided is not a valid URL. Error: {0}
     * 
     */
    public static String CoverityPostBuildStep_getUrlError_0(Object arg1) {
        return holder.format("CoverityPostBuildStep_getUrlError_0", arg1);
    }

    /**
     * The Coverity URL provided is not a valid URL. Error: {0}
     * 
     */
    public static Localizable _CoverityPostBuildStep_getUrlError_0(Object arg1) {
        return new Localizable(holder, "CoverityPostBuildStep_getUrlError_0", arg1);
    }

}
