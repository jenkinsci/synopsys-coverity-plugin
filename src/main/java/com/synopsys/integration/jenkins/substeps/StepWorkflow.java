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

public class StepWorkflow<T> {
    private Node<Void, ?> firstStep;
    private Node<?, T> lastStep;

    protected StepWorkflow(final Node<Void, ?> firstStep, final Node<?, T> lastStep) {
        this.firstStep = firstStep;
        this.lastStep = lastStep;
    }

    public static <R> StepWorkflowBuilder<R> first(final SubStep<Void, R> firstStep) {
        return new StepWorkflowBuilder<>(firstStep);
    }

    public static <R> StepWorkflowBuilder<R> firstCall(final RemoteSubStep<Void, R> firstStep) {
        return new StepWorkflowBuilder<>(firstStep);
    }

    public static <R> StepWorkflow<R> just(final SubStep<Void, R> onlyStep) {
        return new StepWorkflow<>(new Node<>(onlyStep), new Node<>(onlyStep));
    }

    public StepWorkflowResponse<T> run() {
        firstStep.runStep(SubStepResponse.SUCCESS());
        return new StepWorkflowResponse<>(lastStep.response);
    }

    public SubStepResponse<T> runAsSubStep() {
        firstStep.runStep(SubStepResponse.SUCCESS());
        return lastStep.response;
    }

    protected interface Builder<T> {
        <R> Builder<R> thenExecute(final SubStep<T, R> subStep);

        Builder<Void> thenDo(final AbstractVoidSubStep<T> subStep);

        <R> Builder<R> thenGetData(final AbstractSupplyingSubStep<T, R> subStep);

        Builder<Void> thenConsumeData(final AbstractConsumingSubStep<T> subStep);

        <R> Builder<R> thenCall(final RemoteSubStep<T, R> subStep);
    }

    public static class Node<U, S> {
        private final SubStep<U, S> step;
        private Node<S, ?> next;
        private SubStepResponse<S> response;

        public Node(final SubStep<U, S> current) {
            this.step = current;
        }

        public <R> Node<S, R> addNext(final SubStep<S, R> next) {
            final Node<S, R> nextNode = new Node<>(next);
            this.next = nextNode;
            return nextNode;
        }

        public void runStep(final SubStepResponse<U> previousResponse) {
            response = step.run(previousResponse);
            if (next != null) {
                next.runStep(response);
            }
        }
    }

}
