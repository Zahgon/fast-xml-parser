// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** A JavaScript array of values. */
public final class JsArray implements Iterable<Object> {
    private final List<Object> items;

    /** Creates an empty array. */
    public JsArray() {
        items = new ArrayList<>();
    }

    /**
     * Creates an array of the given values.
     *
     * @param values the elements
     */
    public JsArray(List<?> values) {
        items = new ArrayList<>(values);
    }

    /**
     * An array of the given values.
     *
     * @param values the elements
     * @return the array
     */
    public static JsArray of(Object... values) {
        return new JsArray(List.of(values));
    }

    /**
     * {@code array.length}.
     *
     * @return the number of elements
     */
    public int size() {
        return items.size();
    }

    /**
     * {@code array[index]}.
     *
     * @param index the index
     * @return the element, undefined past the end
     */
    public Object get(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : Undefined.INSTANCE;
    }

    /**
     * {@code array[index] = value} for an existing index.
     *
     * @param index the index
     * @param value the value
     */
    public void set(int index, Object value) {
        items.set(index, value);
    }

    /**
     * {@code array.push(value)}.
     *
     * @param value the value
     */
    public void push(Object value) {
        items.add(value);
    }

    /**
     * {@code array.pop()}.
     *
     * @return the removed last element, undefined when empty
     */
    public Object pop() {
        return items.isEmpty() ? Undefined.INSTANCE : items.remove(items.size() - 1);
    }

    /**
     * The elements as a list view.
     *
     * @return the elements
     */
    public List<Object> items() {
        return items;
    }

    @Override
    public Iterator<Object> iterator() {
        return items.iterator();
    }
}
