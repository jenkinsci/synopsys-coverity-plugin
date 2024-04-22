/*
 * synopsys-coverity
 *
 * Copyright (c) 2024 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.exception;

import java.net.MalformedURLException;

import hudson.AbortException;

public class CoverityJenkinsAbortException extends AbortException {
    private static final long serialVersionUID = 7798409254680442327L;

    public CoverityJenkinsAbortException(final String message) {
        super("Coverity cannot be executed: " + message);
    }

    public static CoverityJenkinsAbortException fromMalformedUrlException(final String url, final MalformedURLException malformedURLException) {
        return new CoverityJenkinsAbortException("'" + url + "' is a malformed URL because " + malformedURLException.getMessage());
    }
}
