// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Natural Intelligence (github.com/NaturalIntelligence/xml-naming)
// Ported to Java from xml-naming 0.3.0 and 0.1.0 (src/index.js): the XML 1.0 QName production both versions share.

package io.github.naturalintelligence.fxp.naming;

/**
 * {@code qName(str)} of xml-naming for XML 1.0 (the only version fast-xml-parser ever selects), over UTF-16 code
 * units: an NCName optionally followed by a colon and another NCName.
 */
public final class XmlNaming {
    private XmlNaming() {
    }

    private static boolean isNcNameStart(char c) {
        return (c >= 'A' && c <= 'Z') || c == '_' || (c >= 'a' && c <= 'z') || (c >= 'À' && c <= 'Ö')
                || (c >= 'Ø' && c <= 'ö') || (c >= 'ø' && c <= '˿')
                || (c >= 'Ͱ' && c <= 'ͽ') || (c >= 'Ϳ' && c <= '҆')
                || (c >= '҈' && c <= '῿') || (c >= '‌' && c <= '‍')
                || (c >= '⁰' && c <= '↏') || (c >= 'Ⰰ' && c <= '⿯')
                || (c >= '、' && c <= '퟿') || (c >= '豈' && c <= '﷏')
                || (c >= 'ﷰ' && c <= '�');
    }

    private static boolean isNcNameChar(char c) {
        return isNcNameStart(c) || c == '-' || c == '.' || (c >= '0' && c <= '9') || c == '·'
                || (c >= '̀' && c <= 'ͯ') || (c >= '‿' && c <= '⁀');
    }

    private static int ncNameEnd(String s, int from) {
        if (from >= s.length() || !isNcNameStart(s.charAt(from))) {
            return -1;
        }
        int i = from + 1;
        while (i < s.length() && isNcNameChar(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * {@code qName(str)}.
     *
     * @param s the name
     * @return whether it is a QName
     */
    public static boolean qName(String s) {
        int end = ncNameEnd(s, 0);
        if (end < 0) {
            return false;
        }
        if (end == s.length()) {
            return true;
        }
        return s.charAt(end) == ':' && ncNameEnd(s, end + 1) == s.length();
    }

    private static boolean isNcNameStart11(int c) {
        return (c >= 'A' && c <= 'Z') || c == '_' || (c >= 'a' && c <= 'z') || (c >= 0xC0 && c <= 0x2FF)
                || (c >= 0x370 && c <= 0x37D) || (c >= 0x37F && c <= 0x486) || (c >= 0x488 && c <= 0x1FFF)
                || (c >= 0x200C && c <= 0x200D) || (c >= 0x2070 && c <= 0x218F) || (c >= 0x2C00 && c <= 0x2FEF)
                || (c >= 0x3001 && c <= 0xD7FF) || (c >= 0xF900 && c <= 0xFDCF) || (c >= 0xFDF0 && c <= 0xFFFD)
                || (c >= 0x10000 && c <= 0xEFFFF);
    }

    private static boolean isNcNameChar11(int c) {
        return isNcNameStart11(c) || c == '-' || c == '.' || (c >= '0' && c <= '9') || c == 0xB7
                || (c >= 0x300 && c <= 0x36F) || c == 0x487 || (c >= 0x203F && c <= 0x2040);
    }

    /**
     * {@code qName(str, {xmlVersion: '1.1'})}: the XML 1.1 production, over code points.
     *
     * @param s the name
     * @return whether it is a QName
     */
    public static boolean qName11(String s) {
        int[] cps = s.codePoints().toArray();
        int i = 0;
        for (int part = 0; part < 2; part++) {
            if (i >= cps.length || !isNcNameStart11(cps[i])) {
                return false;
            }
            i++;
            while (i < cps.length && isNcNameChar11(cps[i])) {
                i++;
            }
            if (i == cps.length) {
                return true;
            }
            if (cps[i] != ':' || part == 1) {
                return false;
            }
            i++;
        }
        return false;
    }

    /**
     * fast-xml-parser's own {@code isName} (src/util.js), used by the validator: a Name of its own character
     * ranges, colons allowed anywhere.
     *
     * @param s the name
     * @return whether it is a name
     */
    public static boolean isName(String s) {
        if (s.isEmpty() || !isUtilNameStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(isUtilNameStart(c) || c == '-' || c == '.' || (c >= '0' && c <= '9') || c == '·'
                    || (c >= '̀' && c <= 'ͯ') || (c >= '‿' && c <= '⁀'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUtilNameStart(char c) {
        return c == ':' || (c >= 'A' && c <= 'Z') || c == '_' || (c >= 'a' && c <= 'z')
                || (c >= 'À' && c <= 'Ö') || (c >= 'Ø' && c <= 'ö')
                || (c >= 'ø' && c <= '˿') || (c >= 'Ͱ' && c <= 'ͽ')
                || (c >= 'Ϳ' && c <= '῿') || (c >= '‌' && c <= '‍')
                || (c >= '⁰' && c <= '↏') || (c >= 'Ⰰ' && c <= '⿯')
                || (c >= '、' && c <= '퟿') || (c >= '豈' && c <= '﷏')
                || (c >= 'ﷰ' && c <= '�');
    }
}
