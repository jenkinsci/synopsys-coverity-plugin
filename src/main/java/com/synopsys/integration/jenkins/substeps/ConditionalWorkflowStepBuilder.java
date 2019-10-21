package com.synopsys.integration.jenkins.substeps;

import java.util.function.Predicate;

public class ConditionalWorkflowStepBuilder<P, T> implements StepWorkflow.Builder<T> {
    private final StepWorkflowBuilder<T> conditionalStepWorkflowBuilder;
    private final StepWorkflowBuilder<P> parentBuilder;

    protected ConditionalWorkflowStepBuilder(final StepWorkflowBuilder<P> parentBuilder, final SubStep<Void, T> firstStep) {
        this.parentBuilder = parentBuilder;
        this.conditionalStepWorkflowBuilder = new StepWorkflowBuilder<>(firstStep);
    }

    private <U> ConditionalWorkflowStepBuilder(final ConditionalWorkflowStepBuilder<P, U> currentBuilder, final StepWorkflowBuilder<T> conditionalStepWorkflowBuilder) {
        this.parentBuilder = currentBuilder.parentBuilder;
        this.conditionalStepWorkflowBuilder = conditionalStepWorkflowBuilder;
    }

    public ConditionalWorkflowStepBuilder<P, Void> thenDo(final AbstractVoidSubStep<T> subStep) {
        return new ConditionalWorkflowStepBuilder<>(this, conditionalStepWorkflowBuilder.thenDo(subStep));
    }

    public <R> ConditionalWorkflowStepBuilder<P, R> thenGetData(final AbstractSupplyingSubStep<T, R> subStep) {
        return new ConditionalWorkflowStepBuilder<>(this, conditionalStepWorkflowBuilder.thenGetData(subStep));
    }

    public ConditionalWorkflowStepBuilder<P, Void> thenConsumeData(final AbstractConsumingSubStep<T> subStep) {
        return new ConditionalWorkflowStepBuilder<>(this, conditionalStepWorkflowBuilder.thenConsumeData(subStep));
    }

    public <R> ConditionalWorkflowStepBuilder<P, R> thenCall(final RemoteSubStep<T, R> subStep) {
        return new ConditionalWorkflowStepBuilder<>(this, conditionalStepWorkflowBuilder.thenCall(subStep));
    }

    public <R> ConditionalWorkflowStepBuilder<P, R> thenExecute(final SubStep<T, R> subStep) {
        return new ConditionalWorkflowStepBuilder<>(this, conditionalStepWorkflowBuilder.thenExecute(subStep));
    }

    public <B> StepWorkflowBuilder<Void> butOnlyIf(final B objectToTest, final Predicate<B> tester) {
        return new StepWorkflowBuilder<>(parentBuilder, previousResponse -> {
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
        });
    }
}
