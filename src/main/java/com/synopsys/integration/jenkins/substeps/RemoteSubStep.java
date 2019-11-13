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
package com.synopsys.integration.jenkins.substeps;

import com.synopsys.integration.jenkins.coverity.substeps.remote.CoverityRemoteCallable;

import hudson.remoting.VirtualChannel;

public class RemoteSubStep<T, R> implements SubStep<T, R> {
    private final VirtualChannel virtualChannel;
    private final CoverityRemoteCallable<SubStepResponse<R>> callable;

    public RemoteSubStep(final VirtualChannel virtualChannel, final CoverityRemoteCallable<SubStepResponse<R>> callable) {
        this.virtualChannel = virtualChannel;
        this.callable = callable;
    }

    @Override
    public SubStepResponse<R> run(final SubStepResponse<T> previousResponse) {
        if (previousResponse.isSuccess()) {
            try {
                return virtualChannel.call(callable);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return SubStepResponse.FAILURE(e);
            } catch (final Exception e) {
                return SubStepResponse.FAILURE(e);
            }
        } else {
            return SubStepResponse.FAILURE(previousResponse);
        }
    }
}
