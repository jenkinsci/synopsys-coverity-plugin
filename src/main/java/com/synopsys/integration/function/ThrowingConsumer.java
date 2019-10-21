package com.synopsys.integration.function;

public interface ThrowingConsumer<T, E extends Throwable> {
    /**
     * Applies this function, which may throw an exception, to the given argument.
     * @param t the function argument
     * @return the function result
     */
    void apply(T t) throws E;
}