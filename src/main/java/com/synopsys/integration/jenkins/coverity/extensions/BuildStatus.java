/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions;

import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;

import hudson.model.Result;

public enum BuildStatus implements JenkinsSelectBoxEnum {
    SUCCESS("Success (Log issues only)", Result.SUCCESS),
    FAILURE("Failure", Result.FAILURE),
    UNSTABLE("Unstable", Result.UNSTABLE);

    private final String displayName;
    private final Result result;

    BuildStatus(final String displayName, final Result result) {
        this.displayName = displayName;
        this.result = result;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public Result getResult() {
        return result;
    }

}
