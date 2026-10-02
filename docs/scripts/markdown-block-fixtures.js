#!/usr/bin/env node
//
// Generates the golden fixtures for `Markdown.splitBlocks`, from markdown-it.
//
// `Markdown.splitBlocks` is hand-rolled, and its output is hashed into chunk
// event ids that a quorum signs -- so it is pinned by measurement against the
// reference implementation rather than by anybody's reading of CommonMark. This
// script is that measurement. It runs markdown-it over the corpus below and
// writes the expected chunks as a Kotlin source file in commonTest.
//
//   node docs/scripts/markdown-block-fixtures.js \
//       --markdown-it <path to a markdown-it install> \
//       [--out composeApp/src/commonTest/kotlin/.../MarkdownBlockFixtures.kt]
//
// mantra.press has one: its node_modules/markdown-it. Pass that path.
//
// THE THREE DEPARTURES are applied here, in the oracle, so the Kotlin test is a
// plain equality check and the departures are visible in one place:
//
//   1. A fenced or indented code block yields no chunk. markdown-it's `fence`
//      and `code_block` tokens carry content, so mantra.press turns `code();`
//      into a chunk -- with a trailing newline in its text -- and asks a
//      translator to translate it and the group to sign it as prose.
//   2. A table is one chunk of its own source, not one per cell. Four chunks
//      for a 2x2 table is markdown-it's honest reading, but each costs an event
//      envelope to carry one word and a table's alignment is structure that
//      per-cell translation cannot preserve.
//   3. A link reference definition yields no chunk. This one is free: it is
//      markdown-it's own behaviour with the `reference` block rule left
//      enabled. mantra.press disables it -- reaching for the wrong rule name to
//      stop links rendering -- which drops `[ref]: https://...` through to
//      `paragraph` and makes a translatable chunk of a URL.
//
// `html: false` is markdown-it's default and is kept: an HTML block comes back
// as a paragraph, which is what the Kotlin subset does too.

const fs = require('fs')
const path = require('path')

function arg(name, fallback) {
    const i = process.argv.indexOf(name)
    return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

const mdPath = arg('--markdown-it', null)
if (!mdPath) {
    console.error('--markdown-it <path to a markdown-it install> is required')
    process.exit(2)
}
const outPath = arg(
    '--out',
    'composeApp/src/commonTest/kotlin/press/mantra/compose/text/MarkdownBlockFixtures.kt'
)

const md = require(path.resolve(mdPath))()

/**
 * The corpus. Each entry is a shape that either appears in real chapters or has
 * already caught something. The converter shapes come from mantra.press's own
 * scripts/convert-btc-tex-to-markdown.js, which is what the files being
 * uploaded are made by.
 */
const CORPUS = {
    'one paragraph': 'A single paragraph.\n',
    'two paragraphs': 'First paragraph.\n\nSecond paragraph.\n',
    'lazy continuation': 'First line.\nSecond line of the same paragraph.\n',
    'atx heading apart': '# Chapter one\n\nThe first paragraph.\n',
    'atx heading glued': '# Chapter one\nThe first paragraph.\n',
    'deep atx heading': '#### A fourth-level heading\n\nBody.\n',
    'converter shape': '# Chapter one\nFirst paragraph here.\n\nSecond paragraph here.\n',
    'setext h1': 'Title\n=====\n\nBody.\n',
    'setext h2': 'Subtitle\n--------\n\nBody.\n',
    'tight bullet list': 'Shopping:\n\n- milk\n- bread\n- eggs\n',
    'ordered list': '1. first\n2. second\n',
    'nested list': '- outer\n  - inner\n',
    'blockquote two lines': '> line one\n> line two\n',
    'two blockquotes': '> first\n\n> second\n',
    'thematic break': 'One.\n\n---\n\nTwo.\n',
    'fenced code': 'Text.\n\n```\ncode();\n```\n\nMore.\n',
    'fenced code with info': 'Text.\n\n```kotlin\nval x = 1\n```\n',
    'indented code': 'Text.\n\n    indented code\n\nMore.\n',
    'table': '| a | b |\n|---|---|\n| 1 | 2 |\n',
    'table with prose': 'Before.\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\nAfter.\n',
    'link reference definition': 'See [it][ref].\n\n[ref]: https://example.com\n',
    'inline markup survives': 'A *word* and a [link](https://example.com) and `code`.\n',
    'trailing hard break': 'Line one  \nLine two\n',
    'no trailing newline': 'No newline at the end.',
    'leading blank lines': '\n\nA paragraph after blank lines.\n',
    'crlf already normalised': 'One.\n\nTwo.\n',
    'html block falls back to a paragraph': '<div>html block</div>\n',
    'heading then list then quote':
        '# Title\n\n- one\n- two\n\n> quoted\n\nEnd.\n',
    'multi paragraph blockquote': '> first para\n>\n> second para\n',
    'unicode': 'Ke a leboga.\n\nሰላም ለዓለም።\n',
}

/** The expected chunks for one input, with the three departures applied. */
function expected(source) {
    const lines = source.split('\n')
    const tokens = md.parse(source, {})
    const out = []

    let tableDepth = 0

    for (const token of tokens) {
        if (token.type === 'table_open') {
            // Departure 2: one chunk of the table's own source.
            const [start, end] = token.map
            out.push(lines.slice(start, end).join('\n'))
            tableDepth++
            continue
        }
        if (token.type === 'table_close') {
            tableDepth--
            continue
        }
        if (tableDepth > 0) continue

        // Departure 1: code carries content and is not prose.
        if (token.type === 'fence' || token.type === 'code_block') continue

        // Departure 3 needs no code: with the `reference` rule enabled, a link
        // reference definition produces no token at all.

        if (token.type === 'inline' && token.content) out.push(token.content)
    }

    return out
}

function kotlinString(value) {
    return '"' + value
        .replace(/\\/g, '\\\\')
        .replace(/"/g, '\\"')
        .replace(/\$/g, '\\$')
        .replace(/\n/g, '\\n')
        .replace(/\r/g, '\\r')
        .replace(/\t/g, '\\t') + '"'
}

const entries = Object.entries(CORPUS).map(([name, source]) => {
    const chunks = expected(source)
    return `    Fixture(
        name = ${kotlinString(name)},
        markdown = ${kotlinString(source)},
        chunks = listOf(${chunks.length === 0 ? '' : '\n' + chunks.map(c => '            ' + kotlinString(c)).join(',\n') + ',\n        '}),
    ),`
}).join('\n')

const file = `package press.mantra.compose.text

// GENERATED by docs/scripts/markdown-block-fixtures.js -- do not edit by hand.
//
// The expected output of [Markdown.splitBlocks] for ${Object.keys(CORPUS).length} inputs, taken from
// markdown-it with the three departures recorded in docs/chapter-uploads.md
// applied by the generator. Regenerate with:
//
//   node docs/scripts/markdown-block-fixtures.js \\
//       --markdown-it <path>/node_modules/markdown-it
//
// A change here is a change to what a chunk is, and so to the ids a quorum
// signs. It should arrive with a reason.

data class Fixture(
    val name: String,
    val markdown: String,
    val chunks: List<String>,
)

val MARKDOWN_BLOCK_FIXTURES: List<Fixture> = listOf(
${entries}
)
`

fs.mkdirSync(path.dirname(outPath), { recursive: true })
fs.writeFileSync(outPath, file)
console.log(`wrote ${Object.keys(CORPUS).length} fixtures to ${outPath}`)
