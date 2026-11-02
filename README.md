# fast-xml-parser for Java

Validate XML, parse XML into JavaScript-style objects, and build XML from them, in Java with the JDK standard
library only. This is a port of [fast-xml-parser](https://github.com/NaturalIntelligence/fast-xml-parser) 5.11.2
(and of the runtime dependencies it uses: fast-xml-builder, strnum, anynum, @nodable/entities, path-expression-matcher,
is-unsafe and xml-naming). It accepts the same options and gives the same results, down to number formatting, key
order, entity handling, error messages and the original's quirks.

<img align="right" src="static/img/fxp_logo.png" width="180px" alt="FXP logo"/>

* Validate XML data syntactically (`XMLValidator`).
* Parse XML to objects (`XMLParser`), with attributes, namespaces, CDATA, comments, processing instructions, DOCTYPE
  entities, HTML entities, unpaired tags (e.g. `<br>`), stop nodes (e.g. `<script>`), ordered output and node
  metadata.
* Build XML from objects (`XMLBuilder`), plain or ordered, formatted or compact.
* A `fast-xml-parser` command that answers JSON-lines requests over the whole API.

## Build

JDK 21 and Maven 3.9:

```bash
mvn -B package        # target/fast-xml-parser.jar, tests included
mvn -B test
```

## Using the library

Values are JavaScript-style: objects are `JsObject` (string keys in JavaScript's order: integer keys first),
arrays `JsArray`, numbers `Double`, `null` is JavaScript's `null` and `Js.UNDEFINED` its `undefined`. Options are a
`JsObject` (or `Js.UNDEFINED` for the defaults); callbacks are `JsFunction` lambdas. Errors are thrown as `JsError`,
carrying the original's error `name` and `message`.

```java
import io.github.naturalintelligence.fxp.builder.XMLBuilder;
import io.github.naturalintelligence.fxp.js.JsFunction;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.Json;
import io.github.naturalintelligence.fxp.parser.XMLParser;
import io.github.naturalintelligence.fxp.validator.XMLValidator;

JsObject options = (JsObject) Json.parse("{\"ignoreAttributes\":false}");
options.set("isArray", (JsFunction) args -> "item".equals(args[0]));

Object result = new XMLParser(options).parse("<root><item id=\"1\">01</item></root>");
Json.stringify(result);                       // {"root":{"item":[{"#text":1,"@_id":"1"}]}}

XMLValidator.validate("<a><b></a>", Json.parse("{}"));  // {err: {code, msg, line, col}}

new XMLBuilder(options).build(result);        // <root><item id="1">1</item></root>
```

The options are those of fast-xml-parser; their documentation is in [docs/v4, v5](./docs/v4,%20v5/) (written for
the JavaScript API; the Java classes take the same option names and values):
[parser](./docs/v4,%20v5/2.XMLparseOptions.md), [builder](./docs/v4,%20v5/3.XMLBuilder.md),
[validator](./docs/v4,%20v5/4.XMLValidator.md), [entities](./docs/v4,%20v5/5.Entities.md),
[HTML](./docs/v4,%20v5/6.HTMLParsing.md), [PI tags](./docs/v4,%20v5/7.PITags.md) and
[path expressions](./docs/v4,%20v5/8.%20PathExpression.md).

## The `fast-xml-parser` command

`bin/fast-xml-parser` (or `java -jar target/fast-xml-parser.jar`) takes no arguments. It reads standard input as
UTF-8, treats every non-empty line as one JSON request and writes one JSON response line per request, in order. The
exit status is 0 when every request succeeded and 1 when one failed; any argument prints the usage text on standard
error and exits with 2.

```bash
$ printf '%s\n' '{"op":"parse","xml":"<a x=\"1\">2</a>","options":{"ignoreAttributes":false}}' \
    '{"op":"validate","xml":"<a>"}' | bin/fast-xml-parser
{"result":{"a":{"#text":2,"@_x":"1"}}}
{"result":{"err":{"code":"InvalidTag","msg":"Unclosed tag 'a'.","line":1,"col":1}}}
```

Requests:

| request | does |
| --- | --- |
| `{"op":"parse","xml":X,"bytes":[...],"validate":V,"entities":[[NAME,VALUE],...],"options":O}` | `new XMLParser(O)`, `addEntity` for each pair, then `parse(X or the UTF-8 bytes, V)` |
| `{"op":"validate","xml":S,"options":O}` | `XMLValidator.validate(S, O)` |
| `{"op":"build","data":D,"options":O}` | `new XMLBuilder(O).build(D)` |
| `{"op":"cli","args":[...],"input":S}` | what the original's deprecated `fxparser` command prints for the flags `-ns -a -c -v -V` |

Responses are `{"result":...}` (plus `"calls"` when a callback logs), the `cli` op answers
`{"stdout":...,"stderr":...,"exitCode":...}`, and failures are `{"error":{"name":...,"message":...}}`. Inside
`options`, these JSON shapes stand for the values JSON cannot write:

* `{"$fn":SPEC}`: a callback. SPEC chooses the result with `byPath` (the jPath string, or a matcher's path), `byExpr`
  (path expressions matched against a matcher), `byName` (the first argument) and `else`; results are JSON values or
  `{"$arg":N}`, `{"$undefined":true}`, `{"$throw":MESSAGE}`, `{"$concat":[...]}`, `{"$lower":N}`, `{"$upper":N}` and
  `{"$set":{...},"$delete":[...],"$on":N,"$return":R}`. With `"log":true` every call is reported in `"calls"`, path
  matchers described by their path, tag, namespace, depth, position and counter.
* `{"$regex":SOURCE,"$flags":FLAGS}`: a JavaScript regular expression (ignoreAttributes entries,
  numberParseOptions.skipLike, the builder's entities).
* `{"$expression":PATTERN,"$separator":SEP}`: a path expression for stopNodes.
* `{"$decoder":OPTIONS,"$external":MAP}`: an entity decoder for the entityDecoder option.

With `captureMetaData`, the node metadata (`startIndex`, `endIndex`) appears in a response as one more member,
`@@xmlMetadata`.

## Tests

`mvn test` runs the JUnit suites under `src/test/java`: the parser, validator and builder behaviour of the original
spec files, the ported runtime pieces, the command, and every call of the original jasmine suite replayed against the
original's responses (`src/test/resources/upstream-suite.jsonl`). `spec/assets` holds the documents the suite reads.

## License

MIT, see [LICENSE](./LICENSE). The ported dependencies are MIT licensed as well: fast-xml-builder, anynum, xml-naming,
is-unsafe, strnum and path-expression-matcher (each in its own repository under github.com/NaturalIntelligence) and
@nodable/entities (github.com/nodable/val-parsers). Each
ported file keeps the notice of the code it comes from.
