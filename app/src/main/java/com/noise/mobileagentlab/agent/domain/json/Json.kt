package com.noise.mobileagentlab.agent.domain.json

/**
 * Minimal, dependency-free JSON codec (Phase 11).
 *
 * Just enough to consume structured planner output and to build trace payloads
 * — no serialization plugin, no extra dependency.
 */
class JsonException(message: String) : Exception(message)

sealed interface JsonValue {
    data class Obj(val fields: LinkedHashMap<String, JsonValue> = LinkedHashMap()) : JsonValue {
        operator fun get(key: String): JsonValue? = fields[key]
    }

    data class Arr(val items: List<JsonValue> = emptyList()) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

object Json {

    fun parse(text: String): JsonValue = Parser(text).parseDocument()

    fun write(value: JsonValue): String = buildString { writeValue(value, this) }

    // --- convenience readers -------------------------------------------------

    fun JsonValue.asObjectOrNull(): JsonValue.Obj? = this as? JsonValue.Obj

    fun JsonValue.asStringOrNull(): String? = (this as? JsonValue.Str)?.value

    fun JsonValue.asBooleanOrNull(): Boolean? = (this as? JsonValue.Bool)?.value

    fun JsonValue.asDoubleOrNull(): Double? = (this as? JsonValue.Num)?.value

    fun JsonValue.asArrayOrNull(): JsonValue.Arr? = this as? JsonValue.Arr

    fun JsonValue.Obj.string(key: String): String? = fields[key]?.asStringOrNull()

    fun JsonValue.Obj.boolean(key: String): Boolean? = fields[key]?.asBooleanOrNull()

    private fun writeValue(value: JsonValue, out: StringBuilder) {
        when (value) {
            is JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            is JsonValue.Num -> out.append(formatNumber(value.value))
            is JsonValue.Str -> writeString(value.value, out)
            is JsonValue.Arr -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    writeValue(item, out)
                }
                out.append(']')
            }

            is JsonValue.Obj -> {
                out.append('{')
                var first = true
                for ((key, item) in value.fields) {
                    if (!first) out.append(',')
                    first = false
                    writeString(key, out)
                    out.append(':')
                    writeValue(item, out)
                }
                out.append('}')
            }
        }
    }

    private fun writeString(value: String, out: StringBuilder) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                else -> if (c < ' ') {
                    out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(c)
                }
            }
        }
        out.append('"')
    }

    private fun formatNumber(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite() && Math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    private class Parser(private val text: String) {
        private var index = 0

        fun parseDocument(): JsonValue {
            val value = parseValue()
            skipWhitespace()
            if (index != text.length) throw JsonException("trailing content at $index")
            return value
        }

        private fun parseValue(): JsonValue {
            skipWhitespace()
            if (index >= text.length) throw JsonException("unexpected end of input")
            return when (val c = text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't' -> {
                    expect("true")
                    JsonValue.Bool(true)
                }

                'f' -> {
                    expect("false")
                    JsonValue.Bool(false)
                }

                'n' -> {
                    expect("null")
                    JsonValue.Null
                }

                else -> if (c == '-' || c.isDigit()) parseNumber()
                else throw JsonException("unexpected character '$c' at $index")
            }
        }

        private fun parseObject(): JsonValue.Obj {
            index++ // consume '{'
            val fields = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw JsonException("expected object key at $index")
                val key = parseString()
                skipWhitespace()
                if (peek() != ':') throw JsonException("expected ':' at $index")
                index++
                fields[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return JsonValue.Obj(fields)
                    }

                    else -> throw JsonException("expected ',' or '}' at $index")
                }
            }
        }

        private fun parseArray(): JsonValue.Arr {
            index++ // consume '['
            val items = ArrayList<JsonValue>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return JsonValue.Arr(items)
            }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return JsonValue.Arr(items)
                    }

                    else -> throw JsonException("expected ',' or ']' at $index")
                }
            }
        }

        private fun parseString(): String {
            index++ // consume opening quote
            val out = StringBuilder()
            while (true) {
                if (index >= text.length) throw JsonException("unterminated string")
                when (val c = text[index++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (index >= text.length) throw JsonException("unterminated escape")
                        when (val e = text[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) throw JsonException("bad unicode escape")
                                val hex = text.substring(index, index + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonException("bad unicode escape '$hex'")
                                out.append(code.toChar())
                                index += 4
                            }

                            else -> throw JsonException("bad escape '\\$e'")
                        }
                    }

                    else -> out.append(c)
                }
            }
        }

        private fun parseNumber(): JsonValue.Num {
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] in "-+.eE")) {
                index++
            }
            val raw = text.substring(start, index)
            val value = raw.toDoubleOrNull() ?: throw JsonException("bad number '$raw'")
            return JsonValue.Num(value)
        }

        private fun expect(word: String) {
            if (!text.startsWith(word, index)) throw JsonException("expected '$word' at $index")
            index += word.length
        }

        private fun peek(): Char {
            if (index >= text.length) throw JsonException("unexpected end of input")
            return text[index]
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }
    }
}
