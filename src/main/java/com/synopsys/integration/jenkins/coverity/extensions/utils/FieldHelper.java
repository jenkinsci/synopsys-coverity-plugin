/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import com.synopsys.integration.log.IntLogger;

import hudson.util.ListBoxModel;

public abstract class FieldHelper {
    protected final IntLogger logger;

    public FieldHelper(final IntLogger logger) {
        this.logger = logger;
    }

    protected ListBoxModel.Option wrapAsListBoxModelOption(final String nameValue) {
        return new ListBoxModel.Option(nameValue, nameValue, false);
    }

}
