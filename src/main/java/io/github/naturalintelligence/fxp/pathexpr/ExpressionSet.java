// SPDX-License-Identifier: MIT
// Copyright (c) 2024 Amit Gupta (github.com/NaturalIntelligence/path-expression-matcher)
// Ported to Java from path-expression-matcher 1.6.2 (src/ExpressionSet.js).

package io.github.naturalintelligence.fxp.pathexpr;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A set of expressions, deduplicated by pattern text. The original indexes them by depth and terminal tag; matching
 * any of them is the same as matching each in turn, which is what this port does.
 */
public final class ExpressionSet {
    private final Set<String> patterns = new LinkedHashSet<>();
    private final List<Expression> expressions = new ArrayList<>();

    /**
     * {@code add(expression)}: ignored when an expression with the same pattern is present.
     *
     * @param expression the expression
     */
    public void add(Expression expression) {
        if (patterns.add(expression.pattern())) {
            expressions.add(expression);
        }
    }

    /**
     * {@code size}.
     *
     * @return the number of distinct patterns
     */
    public int size() {
        return patterns.size();
    }

    /**
     * {@code matchesAny(matcher)}.
     *
     * @param matcher the path
     * @return whether any expression matches it
     */
    public boolean matchesAny(PathView matcher) {
        for (Expression expression : expressions) {
            if (matcher.matches(expression)) {
                return true;
            }
        }
        return false;
    }
}
