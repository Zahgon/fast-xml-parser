// SPDX-License-Identifier: MIT
// Author: Amit Gupta (https://solothought.com), MIT License
// Ported to Java from @nodable/entities 3.1.0 (src/entities.js): the tables fast-xml-parser uses.

package io.github.naturalintelligence.fxp.entities;

import io.github.naturalintelligence.fxp.js.JsObject;

/** Named entity tables of @nodable/entities: {@code XML}, {@code COMMON_HTML} and {@code CURRENCY}. */
public final class Entities {
    private static final String[][] XML = {
        {"amp", "&"}, {"apos", "'"}, {"gt", ">"}, {"lt", "<"}, {"quot", "\""},
    };

    private static final String[][] COMMON_HTML = {
        {"nbsp", " "}, {"copy", "©"}, {"reg", "®"}, {"trade", "™"}, {"mdash", "—"},
        {"ndash", "–"}, {"hellip", "…"}, {"laquo", "«"}, {"raquo", "»"}, {"lsquo", "‘"},
        {"rsquo", "’"}, {"ldquo", "“"}, {"rdquo", "”"}, {"bull", "•"}, {"para", "¶"},
        {"sect", "§"}, {"deg", "°"}, {"frac12", "½"}, {"frac14", "¼"}, {"frac34", "¾"},
    };

    private static final String[][] CURRENCY = {
        {"cent", "¢"}, {"pound", "£"}, {"curren", "¤"}, {"yen", "¥"}, {"euro", "€"},
        {"dollar", "$"}, {"fnof", "ƒ"}, {"inr", "₹"}, {"af", "؋"}, {"birr", "ብር"},
        {"peso", "₱"}, {"rub", "₽"}, {"won", "₩"}, {"yuan", "¥"}, {"cedil", "¸"},
    };

    private Entities() {
    }

    private static JsObject table(String[][] entries) {
        JsObject table = new JsObject();
        for (String[] entry : entries) {
            table.defineOwn(entry[0], entry[1]);
        }
        return table;
    }

    /**
     * The five predefined XML entities.
     *
     * @return a new object of them
     */
    public static JsObject xml() {
        return table(XML);
    }

    /**
     * The common HTML entities.
     *
     * @return a new object of them
     */
    public static JsObject commonHtml() {
        return table(COMMON_HTML);
    }

    /**
     * The currency entities.
     *
     * @return a new object of them
     */
    public static JsObject currency() {
        return table(CURRENCY);
    }
}
