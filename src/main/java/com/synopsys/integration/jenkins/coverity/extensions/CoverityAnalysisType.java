/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions;

import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;

public enum CoverityAnalysisType implements JenkinsSelectBoxEnum {
    COV_ANALYZE("Full Analysis"),
    COV_RUN_DESKTOP("Incremental Analysis"),
    THRESHOLD("Determined by change set threshold");

    private String displayName;

    CoverityAnalysisType(final String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

}
