// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

/** A host object exposed to JavaScript-style code (a path matcher, an expression, an entity decoder, ...). */
public interface JsHost {
    /**
     * What {@code String(object)} gives for this object.
     *
     * @return its string form, {@code "[object Object]"} unless the object defines {@code toString}
     */
    default String jsToString() {
        return "[object Object]";
    }
}
