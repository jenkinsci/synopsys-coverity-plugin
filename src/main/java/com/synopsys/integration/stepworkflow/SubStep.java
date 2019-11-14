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
import com.synopsys.integration.function.ThrowingExecutor;
import com.synopsys.integration.function.ThrowingFunction;
import com.synopsys.integration.function.ThrowingSupplier;

@FunctionalInterface
public interface SubStep<T, R> {
    static <T, R, E extends Exception> SubStepResponse<R> defaultExecution(final boolean runCondition, final SubStepResponse<T> previousResponse, final ThrowingSupplier<SubStepResponse<R>, E> successSupplier) {
        try {
            if (runCondition) {
                return successSupplier.get();
            } else {
                return SubStepResponse.FAILURE(previousResponse);
            }
        } catch (final Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return SubStepResponse.FAILURE(e);
        }
    }

    static <T, R, E extends Exception> SubStep<T, R> ofFunction(final ThrowingFunction<T, R, E> throwingFunction) {
        return previousResponse -> SubStep.defaultExecution(previousResponse.isSuccess() && previousResponse.hasData(), previousResponse, () -> {
            final R data = throwingFunction.apply(previousResponse.getData());
            return SubStepResponse.SUCCESS(data);
        });
    }

    static <T, E extends Exception> SubStep<T, Object> ofConsumer(final ThrowingConsumer<T, E> throwingConsumer) {
        return previousResponse -> SubStep.defaultExecution(previousResponse.isSuccess() && previousResponse.hasData(), previousResponse, () -> {
            throwingConsumer.apply(previousResponse.getData());
            return SubStepResponse.SUCCESS();
        });
    }

    static <R, E extends Exception> SubStep<Object, R> ofSupplier(final ThrowingSupplier<R, E> throwingSupplier) {
        return previousResponse -> SubStep.defaultExecution(previousResponse.isSuccess(), previousResponse, () -> {
            final R data = throwingSupplier.get();
            return SubStepResponse.SUCCESS(data);
        });
    }

    static <E extends Exception> SubStep<Object, Object> ofExecutor(final ThrowingExecutor<E> throwingExecutor) {
        return previousResponse -> SubStep.defaultExecution(previousResponse.isSuccess(), previousResponse, () -> {
            throwingExecutor.execute();
            return SubStepResponse.SUCCESS();
        });
    }

    SubStepResponse<R> run(final SubStepResponse<T> previousResponse);

}
