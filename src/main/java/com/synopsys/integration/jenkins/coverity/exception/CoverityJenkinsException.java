/*
 * synopsys-coverity
 *
 * Copyright (c) 2024 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.exception;

import com.synopsys.integration.coverity.exception.CoverityIntegrationException;

public class CoverityJenkinsException extends CoverityIntegrationException {
    public CoverityJenkinsException(final String message) {
        super(message);
    }

    public CoverityJenkinsException(final Throwable cause) {
        super(cause);
    }

    public CoverityJenkinsException(final String message, final Throwable cause) {
        super(message, cause);
    }

}
