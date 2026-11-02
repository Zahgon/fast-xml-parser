// SPDX-License-Identifier: MIT
// Copyright (c) 2024 Amit Gupta (github.com/NaturalIntelligence/path-expression-matcher)
// Ported to Java from path-expression-matcher 1.6.2 (src/Matcher.js).

package io.github.naturalintelligence.fxp.pathexpr;

import io.github.naturalintelligence.fxp.js.JsHost;

/** The read API shared by a {@link Matcher} and its read-only {@link MatcherView}. */
public interface PathView extends JsHost {
    /**
     * The current tag.
     *
     * @return the tag, undefined when the path is empty
     */
    Object getCurrentTag();

    /**
     * The current namespace.
     *
     * @return the namespace, undefined when there is none
     */
    Object getCurrentNamespace();

    /**
     * The current node's position among all its siblings so far.
     *
     * @return the position, -1 when the path is empty
     */
    double getPosition();

    /**
     * How many earlier siblings had the current node's tag (and namespace).
     *
     * @return the counter, -1 when the path is empty
     */
    double getCounter();

    /**
     * The path length.
     *
     * @return the depth
     */
    double getDepth();

    /**
     * Whether the current path matches an expression.
     *
     * @param expression the expression
     * @return true when it matches
     */
    boolean matches(Expression expression);

    @Override
    default String jsToString() {
        return toString();
    }
}
