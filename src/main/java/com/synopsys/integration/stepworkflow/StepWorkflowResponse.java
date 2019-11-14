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
package com.synopsys.integration.stepworkflow;

import com.synopsys.integration.function.ThrowingConsumer;
import com.synopsys.integration.function.ThrowingFunction;

public class StepWorkflowResponse<T> {
    private final boolean workflowSucceeded;
    private final Exception exception;
    private final T data;

    protected StepWorkflowResponse(final SubStepResponse<T> lastSubStepResponse) {
        this.workflowSucceeded = lastSubStepResponse.isSuccess();
        this.exception = lastSubStepResponse.getException();
        this.data = lastSubStepResponse.getData();
    }

    public boolean wasSuccessful() {
        return workflowSucceeded;
    }

    public T getData() {
        return data;
    }

    public Exception getException() {
        return exception;
    }

    public <R, E extends Throwable> R handleResponse(final ThrowingFunction<StepWorkflowResponse<T>, R, E> responseHandler) throws E {
        return responseHandler.apply(this);
    }

    public <E extends Throwable> void consumeResponse(final ThrowingConsumer<StepWorkflowResponse<T>, E> responseHandler) throws E {
        responseHandler.apply(this);
    }
}
