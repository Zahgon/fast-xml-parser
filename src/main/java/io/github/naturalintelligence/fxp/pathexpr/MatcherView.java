// SPDX-License-Identifier: MIT
// Copyright (c) 2024 Amit Gupta (github.com/NaturalIntelligence/path-expression-matcher)
// Ported to Java from path-expression-matcher 1.6.2 (src/Matcher.js).

package io.github.naturalintelligence.fxp.pathexpr;

/** The read-only view of a {@link Matcher} that callbacks receive; it always reflects the matcher's current state. */
public final class MatcherView implements PathView {
    private final Matcher matcher;

    MatcherView(Matcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public Object getCurrentTag() {
        return matcher.getCurrentTag();
    }

    @Override
    public Object getCurrentNamespace() {
        return matcher.getCurrentNamespace();
    }

    @Override
    public double getPosition() {
        return matcher.getPosition();
    }

    @Override
    public double getCounter() {
        return matcher.getCounter();
    }

    @Override
    public double getDepth() {
        return matcher.getDepth();
    }

    @Override
    public boolean matches(Expression expression) {
        return matcher.matches(expression);
    }

    @Override
    public String toString() {
        return matcher.toString();
    }
}
