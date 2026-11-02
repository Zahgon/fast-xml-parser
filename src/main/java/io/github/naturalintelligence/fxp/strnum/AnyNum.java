// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Natural Intelligence (github.com/NaturalIntelligence/anynum)
// Ported to Java from anynum 1.0.1 (anynum.js, digitTable.js), a dependency of strnum.

package io.github.naturalintelligence.fxp.strnum;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Normalises the decimal digits of every script anynum knows, and Unicode minus signs, to ASCII. */
public final class AnyNum {
    private static final int[] SCRIPT_ZEROS = {
        0x0030, 0x0660, 0x06F0, 0x0966, 0x09E6, 0x0A66, 0x0AE6, 0x0B66, 0x0BE6, 0x0C66, 0x0CE6, 0x0D66, 0x0DE6,
        0x0E50, 0x0ED0, 0x0F20, 0x1040, 0x1090, 0x17E0, 0x1810, 0x1946, 0x19D0, 0x1A80, 0x1A90, 0x1B50, 0x1BB0,
        0x1C40, 0x1C50, 0xFF10, 0x1D7CE, 0x1D7D8, 0x1D7E2, 0x1D7EC, 0x1D7F6, 0x104A0, 0x10D30, 0x11066, 0x110F0,
        0x11136, 0x111D0, 0x112F0, 0x11450, 0x114D0, 0x11650, 0x116C0, 0x11730, 0x118E0, 0x11950, 0x11BF0, 0x11C50,
        0x11D50, 0x11DA0, 0x11F50, 0x16A60, 0x16AC0, 0x16B50, 0x1E140, 0x1E2F0, 0x1E4F0, 0x1E950, 0x1FBF0,
    };
    private static final int TABLE_OFFSET = 0x0660;
    private static final int NOT_DIGIT = 0xFF;
    private static final byte[] TABLE = new byte[0xFFFF - TABLE_OFFSET + 1];
    private static final Map<Integer, Integer> HIGH_MAP = new HashMap<>();

    static {
        Arrays.fill(TABLE, (byte) NOT_DIGIT);
        for (int zero : SCRIPT_ZEROS) {
            for (int d = 0; d < 10; d++) {
                int cp = zero + d;
                if (cp <= 0xFFFF) {
                    if (cp >= TABLE_OFFSET) {
                        TABLE[cp - TABLE_OFFSET] = (byte) d;
                    }
                } else {
                    HIGH_MAP.put(cp, d);
                }
            }
        }
    }

    private AnyNum() {
    }

    private static boolean isMinus(int cc) {
        return cc == 0x2212 || cc == 0xFF0D || cc == 0xFE63;
    }

    private static int tableDigit(int cc) {
        return TABLE[cc - TABLE_OFFSET] & 0xFF;
    }

    /**
     * {@code anynum(str)}.
     *
     * @param str the string
     * @return the string with digits and minus signs normalised
     */
    public static String anynum(String str) {
        int len = str.length();
        StringBuilder out = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char cc = str.charAt(i);
            if ((cc >= '0' && cc <= '9') || cc == '-') {
                out.append(cc);
            } else if (cc < TABLE_OFFSET) {
                out.append(isMinus(cc) ? '-' : cc);
            } else if (cc >= 0xD800 && cc <= 0xDBFF) {
                if (i + 1 < len) {
                    char low = str.charAt(i + 1);
                    if (low >= 0xDC00 && low <= 0xDFFF) {
                        Integer d = HIGH_MAP.get(Character.toCodePoint(cc, low));
                        if (d != null) {
                            out.append((char) ('0' + d));
                            i++;
                            continue;
                        }
                    }
                }
                out.append(cc);
            } else if (isMinus(cc)) {
                out.append('-');
            } else {
                int d = tableDigit(cc);
                out.append(d != NOT_DIGIT ? (char) ('0' + d) : cc);
            }
        }
        return out.toString();
    }
}
