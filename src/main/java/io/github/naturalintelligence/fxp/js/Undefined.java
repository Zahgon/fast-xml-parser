// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

/** JavaScript's {@code undefined}; Java {@code null} stands for JavaScript's {@code null}. */
public enum Undefined {
    /** The only value. */
    INSTANCE;

    @Override
    public String toString() {
        return "undefined";
    }
}
