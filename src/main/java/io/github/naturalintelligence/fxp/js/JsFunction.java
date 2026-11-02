// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

/** A JavaScript function value: callbacks handed to the library, and the built-in functions it can reach. */
@FunctionalInterface
public interface JsFunction {
    /**
     * Calls the function.
     *
     * @param args the arguments; missing ones are {@link Undefined#INSTANCE} to the callee
     * @return the result, {@link Undefined#INSTANCE} for none
     */
    Object call(Object... args);

    /**
     * The function's {@code length} property.
     *
     * @return the number of declared parameters
     */
    default int length() {
        return 0;
    }
}
