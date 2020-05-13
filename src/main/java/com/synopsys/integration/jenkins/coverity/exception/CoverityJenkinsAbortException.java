/**
 * synopsys-coverity
 *
 * Copyright (c) 2020 Synopsys, Inc.
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
