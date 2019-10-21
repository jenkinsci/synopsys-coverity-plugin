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

public class StepWorkflowBuilder<T> implements StepWorkflow.Builder<T> {
    private final StepWorkflow.Node<Void, ?> firstStep;
    private final StepWorkflow.Node<?, T> lastStep;

    protected StepWorkflowBuilder(final SubStep<Void, T> firstStep) {
        final StepWorkflow.Node<Void, T> firstNode = new StepWorkflow.Node<>(firstStep);
        this.firstStep = firstNode;
        this.lastStep = firstNode;
    }

    protected <S> StepWorkflowBuilder(final StepWorkflowBuilder<S> stepWorkflowBuilder, final SubStep<S, T> nextStep) {
        this.firstStep = stepWorkflowBuilder.firstStep;
        this.lastStep = stepWorkflowBuilder.lastStep.addNext(nextStep);
    }

    public <R> StepWorkflowBuilder<R> thenExecute(final SubStep<T, R> subStep) {
        return new StepWorkflowBuilder<>(this, subStep);
    }

    public StepWorkflowBuilder<Void> thenDo(final AbstractVoidSubStep<T> subStep) {
        return new StepWorkflowBuilder<>(this, subStep);
    }

    public <R> StepWorkflowBuilder<R> thenGetData(final AbstractSupplyingSubStep<T, R> subStep) {
        return new StepWorkflowBuilder<>(this, subStep);
    }

    public StepWorkflowBuilder<Void> thenConsumeData(final AbstractConsumingSubStep<T> subStep) {
        return new StepWorkflowBuilder<>(this, subStep);
    }

    public <R> StepWorkflowBuilder<R> thenCall(final RemoteSubStep<T, R> subStep) {
        return new StepWorkflowBuilder<>(this, subStep);
    }

    public <R> ConditionalWorkflowStepBuilder<T, R> andSometimes(final SubStep<Void, R> subStep) {
        return new ConditionalWorkflowStepBuilder<>(this, subStep);
    }

    public StepWorkflow<T> build() {
        return new StepWorkflow<>(firstStep, lastStep);
    }

    public StepWorkflowResponse<T> run() {
        return this.build().run();
    }

}
