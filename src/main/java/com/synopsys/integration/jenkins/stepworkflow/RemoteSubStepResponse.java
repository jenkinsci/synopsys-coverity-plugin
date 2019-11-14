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

import com.synopsys.integration.stepworkflow.SubStepResponse;

public class RemoteSubStepResponse<T> implements Serializable {
    private static final long serialVersionUID = -2531902620195156395L;
    private final boolean subStepSucceeded;
    private final Exception exception;
    private final T data;

    protected RemoteSubStepResponse(final boolean subStepSucceeded, final T data, final Exception e) {
        this.subStepSucceeded = subStepSucceeded;
        this.exception = e;
        this.data = data;
    }

    // You should not return no data on a success unless you explicitly claim to return no data -- rotte OCT 9 2019
    public static RemoteSubStepResponse<Object> SUCCESS() {
        return SUCCESS(null);
    }

    public static <S> RemoteSubStepResponse<S> SUCCESS(final S data) {
        return new RemoteSubStepResponse<>(true, data, null);
    }

    public static <S> RemoteSubStepResponse<S> FAILURE(final RemoteSubStepResponse previousFailure) {
        return FAILURE(previousFailure.exception);
    }

    public static <S> RemoteSubStepResponse<S> FAILURE(final Exception e) {
        return new RemoteSubStepResponse<>(false, null, e);
    }

    public boolean isSuccess() {
        return subStepSucceeded;
    }

    public boolean isFailure() {
        return !subStepSucceeded;
    }

    public boolean hasData() {
        return data != null;
    }

    public T getData() {
        return data;
    }

    public boolean hasException() {
        return exception != null;
    }

    public Exception getException() {
        return exception;
    }

    public SubStepResponse<T> toSubStepResponse() {
        return new SubStepResponse<>(subStepSucceeded, data, exception);
    }
}
