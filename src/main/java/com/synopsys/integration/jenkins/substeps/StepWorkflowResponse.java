package com.synopsys.integration.jenkins.substeps;

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
