/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions;

import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;

public enum CoverityCaptureType implements JenkinsSelectBoxEnum {
    COV_BUILD("Build"),
    COV_CAPTURE_PROJECT("Buildless Capture (Project)"),
    COV_CAPTURE_SCM("Buildless Capture (SCM)");

    private String displayName;

    CoverityCaptureType(final String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
}
