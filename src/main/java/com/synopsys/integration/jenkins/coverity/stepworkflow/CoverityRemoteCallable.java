/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import org.jenkinsci.remoting.Role;
import org.jenkinsci.remoting.RoleChecker;

import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;

import hudson.remoting.Callable;

public abstract class CoverityRemoteCallable<T> implements Callable<T, IntegrationException> {
    private static final long serialVersionUID = -4096882757092525358L;
    protected final CoverityJenkinsIntLogger logger;

    public CoverityRemoteCallable(CoverityJenkinsIntLogger logger) {
        this.logger = logger;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, new Role(this.getClass()));
    }
}
