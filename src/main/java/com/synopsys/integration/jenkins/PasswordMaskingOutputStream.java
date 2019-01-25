/**
 * synopsys-coverity
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
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
package com.synopsys.integration.jenkins;

import static com.synopsys.integration.coverity.executable.Executable.MASKED_PASSWORD;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang.StringUtils;

import hudson.console.LineTransformationOutputStream;

public class PasswordMaskingOutputStream extends LineTransformationOutputStream {
    private final OutputStream wrappedOutputStream;
    private final String passwordToMask;

    public PasswordMaskingOutputStream(final OutputStream wrappedOutputStream, final String passwordToMask) {
        this.wrappedOutputStream = wrappedOutputStream;
        this.passwordToMask = passwordToMask;
    }

    @Override
    protected void eol(final byte[] bytes, final int len) throws IOException {
        String line = new String(bytes, 0, len, StandardCharsets.UTF_8);

        if (StringUtils.isNotBlank(passwordToMask)) {
            line = line.replaceAll(passwordToMask, MASKED_PASSWORD);
        }

        wrappedOutputStream.write(line.getBytes(StandardCharsets.UTF_8));
    }

}