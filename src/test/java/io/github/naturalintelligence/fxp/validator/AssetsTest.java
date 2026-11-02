// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (spec/large_spec.js and the asset files the suite reads).

package io.github.naturalintelligence.fxp.validator;

import static io.github.naturalintelligence.fxp.TestJson.json;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.naturalintelligence.fxp.js.Js;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Validates the documents of spec/assets, the fixtures the upstream suite reads. */
class AssetsTest {
    private static final Path ASSETS = Path.of("spec", "assets");

    private static String read(String name) throws IOException {
        return Files.readString(ASSETS.resolve(name), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"by.svg", "complex.xml", "crlf.xml", "mini-sample.xml", "ptest.xml",
        "ptest_with_prolog.xml", "sample.xml", "sp.svg"})
    void acceptsTheWellFormedAssets(String name) throws IOException {
        assertEquals("true", json(XMLValidator.validate(read(name), Js.UNDEFINED)), name);
    }

    @Test
    void reportsTheFirstProblemOfTheInvalidAssets() throws IOException {
        assertEquals("{\"err\":{\"code\":\"InvalidTag\",\"msg\":\"Expected closing tag 'selfclosing' (opened in line "
                + "11, col 2) instead of closing tag 'person'.\",\"line\":27,\"col\":5}}",
                json(XMLValidator.validate(read("invalid.xml"), Js.UNDEFINED)));
        assertEquals("{\"err\":{\"code\":\"InvalidAttr\",\"msg\":\"boolean attribute 'boolean' is not allowed.\","
                + "\"line\":12,\"col\":22}}", json(XMLValidator.validate(read("mixed.xml"), Js.UNDEFINED)));
        assertEquals("{\"err\":{\"code\":\"InvalidXml\",\"msg\":\"Multiple possible root nodes found.\",\"line\":48,"
                + "\"col\":46}}", json(XMLValidator.validate(read("midsize.xml"), Js.UNDEFINED)));
    }

    @Test
    void validatesTheBigAsset() throws IOException {
        assertEquals("true", json(XMLValidator.validate(read("large.xml"), Js.UNDEFINED)));
    }
}
