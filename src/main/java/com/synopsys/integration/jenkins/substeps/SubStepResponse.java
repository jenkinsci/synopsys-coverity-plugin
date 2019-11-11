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

public class SubStepResponse<T> {
    private final boolean subStepSucceeded;
    private final Exception exception;
    private final T data;

    protected SubStepResponse(final boolean subStepSucceeded, final T data, final Exception e) {
        this.subStepSucceeded = subStepSucceeded;
        this.exception = e;
        this.data = data;
    }

    // You should not return no data on a success unless you explicitly claim to return no data -- rotte OCT 9 2019
    public static SubStepResponse<Object> SUCCESS() {
        return SUCCESS(null);
    }

    public static <S> SubStepResponse<S> SUCCESS(final S data) {
        return new SubStepResponse<>(true, data, null);
    }

    public static <S> SubStepResponse<S> FAILURE(final SubStepResponse previousFailure) {
        return FAILURE(previousFailure.exception);
    }

    public static <S> SubStepResponse<S> FAILURE(final Exception e) {
        return new SubStepResponse<>(false, null, e);
    }

    public static SubStepResponse<Void> COPY_RESPONSE_WITHOUT_DATA(final SubStepResponse<?> previousResponse) {
        return new SubStepResponse<>(previousResponse.subStepSucceeded, null, previousResponse.exception);
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
}
