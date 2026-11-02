// SPDX-License-Identifier: MIT
// Copyright (c) 2024 Amit Gupta (github.com/NaturalIntelligence/path-expression-matcher)
// Ported to Java from path-expression-matcher 1.6.2 (src/Matcher.js).

package io.github.naturalintelligence.fxp.pathexpr;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks the current tag path with each node's sibling position and same-name counter, keeps the attribute values of
 * the current node only, and matches the path against {@link Expression}s.
 */
public final class Matcher implements PathView {
    private final List<Node> path = new ArrayList<>();
    private final List<Level> siblingStacks = new ArrayList<>();
    private final MatcherView view = new MatcherView(this);

    /** A path node; {@code values} is undefined except on the current node. */
    private static final class Node {
        private final Object tag;
        private final double position;
        private final double counter;
        private final Object namespace;
        private Object values;

        private Node(Object tag, double position, double counter, Object namespace, Object values) {
            this.tag = tag;
            this.position = position;
            this.counter = counter;
            this.namespace = namespace;
            this.values = values;
        }
    }

    /** The children seen so far at one depth. */
    private static final class Level {
        private final Map<String, Double> counts = new HashMap<>();
        private double total;
    }

    /**
     * {@code push(tagName, attrValues, namespace)}.
     *
     * @param tagName the tag
     * @param attrValues the attribute values (null or undefined for none)
     * @param namespace the namespace (null or undefined for none)
     */
    public void push(Object tagName, Object attrValues, Object namespace) {
        if (!path.isEmpty()) {
            path.get(path.size() - 1).values = Js.UNDEFINED;
        }
        int currentLevel = path.size();
        while (siblingStacks.size() <= currentLevel) {
            siblingStacks.add(null);
        }
        Level level = siblingStacks.get(currentLevel);
        if (level == null) {
            level = new Level();
            siblingStacks.set(currentLevel, level);
        }
        String siblingKey = Js.truthy(namespace) ? Js.toStr(namespace) + ":" + Js.toStr(tagName) : Js.toStr(tagName);
        double counter = level.counts.getOrDefault(siblingKey, 0.0);
        double position = level.total;
        level.counts.put(siblingKey, counter + 1);
        level.total++;
        path.add(new Node(tagName, position, counter, Js.isNullish(namespace) ? Js.UNDEFINED : namespace,
                Js.isNullish(attrValues) ? Js.UNDEFINED : attrValues));
    }

    /**
     * {@code push(tagName)}.
     *
     * @param tagName the tag
     */
    public void push(Object tagName) {
        push(tagName, null, null);
    }

    /** {@code pop()}: removes the current node. */
    public void pop() {
        if (path.isEmpty()) {
            return;
        }
        path.remove(path.size() - 1);
        while (siblingStacks.size() > path.size() + 1) {
            siblingStacks.remove(siblingStacks.size() - 1);
        }
    }

    /**
     * {@code updateCurrent(attrValues)}: replaces the current node's attribute values.
     *
     * @param attrValues the values (ignored when null or undefined)
     */
    public void updateCurrent(Object attrValues) {
        if (!path.isEmpty() && !Js.isNullish(attrValues)) {
            path.get(path.size() - 1).values = attrValues;
        }
    }

    /** {@code reset()}. */
    public void reset() {
        path.clear();
        siblingStacks.clear();
    }

    @Override
    public Object getCurrentTag() {
        return path.isEmpty() ? Js.UNDEFINED : path.get(path.size() - 1).tag;
    }

    @Override
    public Object getCurrentNamespace() {
        return path.isEmpty() ? Js.UNDEFINED : path.get(path.size() - 1).namespace;
    }

    @Override
    public double getPosition() {
        return path.isEmpty() ? -1 : path.get(path.size() - 1).position;
    }

    @Override
    public double getCounter() {
        return path.isEmpty() ? -1 : path.get(path.size() - 1).counter;
    }

    @Override
    public double getDepth() {
        return path.size();
    }

    /**
     * {@code readOnly()}.
     *
     * @return the read-only view of this matcher
     */
    public MatcherView readOnly() {
        return view;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                out.append('.');
            }
            Node node = path.get(i);
            if (Js.truthy(node.namespace)) {
                out.append(Js.toStr(node.namespace)).append(':').append(Js.toStr(node.tag));
            } else if (!Js.isNullish(node.tag)) {
                out.append(Js.toStr(node.tag));
            }
        }
        return out.toString();
    }

    @Override
    public boolean matches(Expression expression) {
        List<Expression.Segment> segments = expression.segments();
        if (segments.isEmpty()) {
            return false;
        }
        return expression.hasDeepWildcard() ? matchWithDeepWildcard(segments) : matchSimple(segments);
    }

    private boolean matchSimple(List<Expression.Segment> segments) {
        if (path.size() != segments.size()) {
            return false;
        }
        for (int i = 0; i < segments.size(); i++) {
            if (!matchSegment(segments.get(i), path.get(i), i == path.size() - 1)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchWithDeepWildcard(List<Expression.Segment> segments) {
        int pathIdx = path.size() - 1;
        int segIdx = segments.size() - 1;
        while (segIdx >= 0 && pathIdx >= 0) {
            Expression.Segment segment = segments.get(segIdx);
            if (segment.deepWildcard()) {
                segIdx--;
                if (segIdx < 0) {
                    return true;
                }
                Expression.Segment nextSeg = segments.get(segIdx);
                boolean found = false;
                for (int i = pathIdx; i >= 0; i--) {
                    if (matchSegment(nextSeg, path.get(i), i == path.size() - 1)) {
                        pathIdx = i - 1;
                        segIdx--;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            } else {
                if (!matchSegment(segment, path.get(pathIdx), pathIdx == path.size() - 1)) {
                    return false;
                }
                pathIdx--;
                segIdx--;
            }
        }
        return segIdx < 0;
    }

    private static boolean matchSegment(Expression.Segment segment, Node node, boolean isCurrentNode) {
        if (segment.deepWildcard()) {
            return node.tag == Js.UNDEFINED;
        }
        if (!segment.tag().equals("*") && !Js.strictEquals(segment.tag(), node.tag)) {
            return false;
        }
        if (segment.namespace() != null && !segment.namespace().equals("*")
                && !Js.strictEquals(segment.namespace(), node.namespace)) {
            return false;
        }
        if (segment.attrName() != null) {
            if (!isCurrentNode || !Js.truthy(node.values) || !Js.in(segment.attrName(), node.values)) {
                return false;
            }
            if (segment.attrValue() != null && !Js.toStr(attrValue(node.values, segment.attrName()))
                    .equals(segment.attrValue())) {
                return false;
            }
        }
        if (segment.position() != null) {
            if (!isCurrentNode) {
                return false;
            }
            double counter = node.counter;
            switch (segment.position()) {
                case "first":
                    return counter == 0;
                case "odd":
                    return counter % 2 == 1;
                case "even":
                    return counter % 2 == 0;
                case "nth":
                    return counter == segment.positionValue();
                default:
                    return true;
            }
        }
        return true;
    }

    private static Object attrValue(Object values, String name) {
        return values instanceof JsObject object ? object.get(name) : Js.get(values, name);
    }
}
