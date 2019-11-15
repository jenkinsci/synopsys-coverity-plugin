/**
 * synopsys-coverity
 *
 * Copyright (c) 2019 Synopsys, Inc.
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
package com.synopsys.integration.jenkins.stepworkflow;

import java.io.Serializable;

import com.synopsys.integration.stepworkflow.SubStep;
import com.synopsys.integration.stepworkflow.SubStepResponse;

import hudson.remoting.VirtualChannel;

public class RemoteSubStep<R extends Serializable> implements SubStep<Object, R> {
    private final VirtualChannel virtualChannel;
    private final CoverityRemoteCallable<RemoteSubStepResponse<R>> callable;

    private RemoteSubStep(final VirtualChannel virtualChannel, final CoverityRemoteCallable<RemoteSubStepResponse<R>> callable) {
        this.virtualChannel = virtualChannel;
        this.callable = callable;
    }

    public static <S extends Serializable> RemoteSubStep<S> of(final VirtualChannel virtualChannel, final CoverityRemoteCallable<RemoteSubStepResponse<S>> callable) {
        return new RemoteSubStep<>(virtualChannel, callable);
    }

    @Override
    public SubStepResponse<R> run(final SubStepResponse<?> previousResponse) {
        return SubStep.defaultExecution(previousResponse.isSuccess(), previousResponse, () -> {
            final RemoteSubStepResponse<R> thisResponse = virtualChannel.call(callable);
            return thisResponse.toSubStepResponse();
        });
    }

}
