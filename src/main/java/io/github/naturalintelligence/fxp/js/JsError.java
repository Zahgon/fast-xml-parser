// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

/** A thrown JavaScript error: its {@code name} ("Error", "TypeError", ...) and {@code message}. */
public final class JsError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The error's {@code name} property. */
    private final String name;

    /**
     * Creates an error.
     *
     * @param name the {@code name} property
     * @param message the {@code message} property
     */
    public JsError(String name, String message) {
        super(message, null, false, false);
        this.name = name;
    }

    /**
     * {@code new Error(message)}.
     *
     * @param message the message
     * @return the error
     */
    public static JsError error(String message) {
        return new JsError("Error", message);
    }

    /**
     * {@code new TypeError(message)}.
     *
     * @param message the message
     * @return the error
     */
    public static JsError typeError(String message) {
        return new JsError("TypeError", message);
    }

    /**
     * {@code new SyntaxError(message)}.
     *
     * @param message the message
     * @return the error
     */
    public static JsError syntaxError(String message) {
        return new JsError("SyntaxError", message);
    }

    /**
     * The error's {@code name} property.
     *
     * @return the name
     */
    public String name() {
        return name;
    }
}
