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
package com.sig.integration.coverity.dsl;

import com.sig.integration.coverity.common.RepeatableCommand;

import javaposse.jobdsl.dsl.Context;

public class CoverityDslContext implements Context {
    private RepeatableCommand[] commands;

    public void commands(final RepeatableCommand[] commands) {
        setCommands(commands);
    }

    public RepeatableCommand[] getCommands() {
        return this.commands;
    }

    public void setCommands(final RepeatableCommand[] commands) {
        this.commands = commands;
    }
}
