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

import java.util.function.Predicate;

public class StepWorkflow<T> {
    protected FlowController<Object, ?> firstStep;
    protected FlowController<?, T> lastStep;

    protected StepWorkflow(final FlowController<Object, ?> firstStep, final FlowController<?, T> lastStep) {
        this.firstStep = firstStep;
        this.lastStep = lastStep;
    }

    public static <R> Builder<R> first(final SubStep<Object, R> firstStep) {
        return new Builder<>(firstStep);
    }

    public static <R> StepWorkflow<R> just(final SubStep<Object, R> onlyStep) {
        return new StepWorkflow<>(new FlowController<>(onlyStep), new FlowController<>(onlyStep));
    }

    public StepWorkflowResponse<T> run() {
        firstStep.runStep(SubStepResponse.SUCCESS());
        return new StepWorkflowResponse<>(lastStep.response);
    }

    private SubStepResponse<T> runAsSubStep() {
        firstStep.runStep(SubStepResponse.SUCCESS());
        return lastStep.response;
    }

    protected static class FlowController<U, S> {
        private final SubStep<U, S> step;
        private FlowController<? super S, ?> next;
        private SubStepResponse<S> response;

        protected FlowController(final SubStep<U, S> current) {
            this.step = current;
        }

        protected SubStepResponse<S> getResponse() {
            return response;
        }

        protected <R> FlowController<? super S, R> addNext(final SubStep<? super S, R> nextStep) {
            final FlowController<? super S, R> next = new FlowController<>(nextStep);
            this.next = next;
            return next;
        }

        protected void runStep(final SubStepResponse<? extends U> previousResponse) {
            response = step.run(previousResponse);
            if (next != null) {
                next.runStep(response);
            }
        }
    }

    public static class Builder<T> {
        private final FlowController<Object, ?> firstStep;
        private final FlowController<?, T> lastStep;

        private Builder(final SubStep<Object, T> firstStep) {
            final FlowController<Object, T> firstFlowController = new FlowController<>(firstStep);
            this.firstStep = firstFlowController;
            this.lastStep = firstFlowController;
        }

        private <S> Builder(final Builder<S> stepWorkflowBuilder, final SubStep<? super S, T> nextStep) {
            this.firstStep = stepWorkflowBuilder.firstStep;
            this.lastStep = stepWorkflowBuilder.lastStep.addNext(nextStep);
        }

        public <R> Builder<R> then(final SubStep<? super T, R> subStep) {
            return new Builder<>(this, subStep);
        }

        public <R> ConditionalBuilder<T, R> andSometimes(final SubStep<Object, R> subStep) {
            return new ConditionalBuilder<>(this, subStep);
        }

        public StepWorkflow<T> build() {
            return new StepWorkflow<>(firstStep, lastStep);
        }

        public StepWorkflowResponse<T> run() {
            return this.build().run();
        }

    }

    public static class ConditionalBuilder<P, T> {
        private final Builder<T> conditionalStepWorkflowBuilder;
        private final Builder<P> parentBuilder;

        private ConditionalBuilder(final Builder<P> parentBuilder, final SubStep<Object, T> firstStep) {
            this.parentBuilder = parentBuilder;
            this.conditionalStepWorkflowBuilder = new Builder<>(firstStep);
        }

        private <U> ConditionalBuilder(final ConditionalBuilder<P, U> currentBuilder, final Builder<T> conditionalStepWorkflowBuilder) {
            this.parentBuilder = currentBuilder.parentBuilder;
            this.conditionalStepWorkflowBuilder = conditionalStepWorkflowBuilder;
        }

        public <R> ConditionalBuilder<P, R> then(final SubStep<T, R> subStep) {
            return new ConditionalBuilder<>(this, conditionalStepWorkflowBuilder.then(subStep));
        }

        public <B> Builder<Object> butOnlyIf(final B objectToTest, final Predicate<B> tester) {
            return this.build(previousResponse -> this.runConditionalWorkflow(objectToTest, tester, previousResponse));
        }

        private Builder<Object> build(final SubStep<P, Object> workflowAsSubstep) {
            return new Builder<>(parentBuilder, workflowAsSubstep);
        }

        private <B> SubStepResponse<Object> runConditionalWorkflow(final B objectToTest, final Predicate<B> tester, final SubStepResponse<? extends P> previousResponse) {
            if (previousResponse.isSuccess()) {
                if (tester.test(objectToTest)) {
                    final SubStepResponse<T> response = conditionalStepWorkflowBuilder.build().runAsSubStep();
                    // The test occurs at runtime but our builder API demands a promise of typing at compile time. Since we don't know until runtime, we cannot promise data. -- rotte OCT 07 2019
                    return new SubStepResponse<>(response.isSuccess(), null, response.getException());
                }
                return SubStepResponse.SUCCESS();
            } else {
                return SubStepResponse.FAILURE(previousResponse);
            }
        }
    }

}
