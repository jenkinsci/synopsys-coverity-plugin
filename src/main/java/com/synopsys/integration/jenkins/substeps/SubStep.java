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

import com.synopsys.integration.function.ThrowingConsumer;
import com.synopsys.integration.function.ThrowingExecutor;
import com.synopsys.integration.function.ThrowingFunction;
import com.synopsys.integration.function.ThrowingOperator;
import com.synopsys.integration.function.ThrowingSupplier;

@FunctionalInterface
public interface SubStep<T, R> {
    static <T, R, E extends Exception> SubStepResponse<R> createDefaultSubStep(final boolean runCondition, final SubStepResponse<T> previousResponse, final ThrowingSupplier<SubStepResponse<R>, E> successSupplier) {
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

    static <T, R, E extends Exception> SubStep<T, R> of(final ThrowingFunction<T, R, E> throwingFunction) {
        return previousResponse -> SubStep.createDefaultSubStep(previousResponse.isSuccess() && previousResponse.hasData(), previousResponse, () -> {
            final R data = throwingFunction.apply(previousResponse.getData());
            return SubStepResponse.SUCCESS(data);
        });
    }

    SubStepResponse<R> run(final SubStepResponse<T> previousResponse);

    @FunctionalInterface
    interface Consuming<T> extends SubStep<T, Object> {
        static <T, E extends Exception> SubStep.Consuming<T> of(final ThrowingConsumer<T, E> throwingConsumer) {
            return previousResponse -> SubStep.createDefaultSubStep(previousResponse.isSuccess() && previousResponse.hasData(), previousResponse, () -> {
                throwingConsumer.apply(previousResponse.getData());
                return SubStepResponse.SUCCESS();
            });
        }
    }

    @FunctionalInterface
    interface Operating<T> extends SubStep<T, T> {
        static <T, E extends Exception> SubStep.Operating<T> of(final ThrowingOperator<T, E> throwingOperator) {
            return previousResponse -> SubStep.createDefaultSubStep(previousResponse.isSuccess() && previousResponse.hasData(), previousResponse, () -> {
                final T data = throwingOperator.operate(previousResponse.getData());
                return SubStepResponse.SUCCESS(data);
            });
        }
    }

    @FunctionalInterface
    interface Supplying<R> extends SubStep<Object, R> {
        static <R, E extends Exception> SubStep.Supplying<R> of(final ThrowingSupplier<R, E> throwingSupplier) {
            return previousResponse -> SubStep.createDefaultSubStep(previousResponse.isSuccess(), previousResponse, () -> {
                final R data = throwingSupplier.get();
                return SubStepResponse.SUCCESS(data);
            });
        }
    }

    @FunctionalInterface
    interface Executing extends Operating<Object> {
        static <E extends Exception> SubStep.Executing of(final ThrowingExecutor<E> throwingExecutor) {
            return previousResponse -> SubStep.createDefaultSubStep(previousResponse.isSuccess(), previousResponse, () -> {
                throwingExecutor.execute();
                return SubStepResponse.SUCCESS();
            });
        }
    }

}
