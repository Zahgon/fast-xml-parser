// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

/**
 * The WHATWG UTF-8 decoder ({@code TextDecoder} with {@code ignoreBOM}, {@code Buffer.toString('utf8')}): every
 * maximal ill-formed subsequence becomes one U+FFFD and a byte order mark is kept.
 */
public final class Utf8 {
    private static final char REPLACEMENT = '�';

    private Utf8() {
    }

    /**
     * Decodes bytes.
     *
     * @param bytes the bytes
     * @param length the number of bytes to decode from the start
     * @return the decoded string
     */
    public static String decode(byte[] bytes, int length) {
        StringBuilder out = new StringBuilder(length);
        int codePoint = 0;
        int needed = 0;
        int seen = 0;
        int lower = 0x80;
        int upper = 0xBF;
        int i = 0;
        while (i < length) {
            int b = bytes[i] & 0xFF;
            if (needed == 0) {
                i++;
                if (b <= 0x7F) {
                    out.append((char) b);
                } else if (b >= 0xC2 && b <= 0xDF) {
                    needed = 1;
                    codePoint = b & 0x1F;
                } else if (b >= 0xE0 && b <= 0xEF) {
                    lower = b == 0xE0 ? 0xA0 : 0x80;
                    upper = b == 0xED ? 0x9F : 0xBF;
                    needed = 2;
                    codePoint = b & 0xF;
                } else if (b >= 0xF0 && b <= 0xF4) {
                    lower = b == 0xF0 ? 0x90 : 0x80;
                    upper = b == 0xF4 ? 0x8F : 0xBF;
                    needed = 3;
                    codePoint = b & 0x7;
                } else {
                    out.append(REPLACEMENT);
                }
                continue;
            }
            if (b < lower || b > upper) {
                codePoint = 0;
                needed = 0;
                seen = 0;
                lower = 0x80;
                upper = 0xBF;
                out.append(REPLACEMENT);
                continue;
            }
            i++;
            lower = 0x80;
            upper = 0xBF;
            codePoint = (codePoint << 6) | (b & 0x3F);
            seen++;
            if (seen == needed) {
                out.appendCodePoint(codePoint);
                codePoint = 0;
                needed = 0;
                seen = 0;
            }
        }
        if (needed != 0) {
            out.append(REPLACEMENT);
        }
        return out.toString();
    }
}
