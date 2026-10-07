package com.noise.mobileagentlab.agent.domain.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    private fun parse(text: String): JsonValue = Json.parse(text)

    @Test
    fun `parses primitives`() {
        assertEquals(JsonValue.Str("hi"), parse("\"hi\""))
        assertEquals(JsonValue.Num(42.0), parse("42"))
        assertEquals(JsonValue.Num(-3.5), parse("-3.5"))
        assertEquals(JsonValue.Bool(true), parse("true"))
        assertEquals(JsonValue.Bool(false), parse("false"))
        assertEquals(JsonValue.Null, parse("null"))
    }

    @Test
    fun `parses objects and arrays`() {
        val obj = parse("""{"a":1,"b":"two","c":[1,2],"d":{"e":true}}""") as JsonValue.Obj
        assertEquals(JsonValue.Num(1.0), obj["a"])
        assertEquals("two", (obj["b"] as JsonValue.Str).value)
        val arr = obj["c"] as JsonValue.Arr
        assertEquals(2, arr.items.size)
        assertTrue(obj["d"] is JsonValue.Obj)
        assertTrue(obj["missing"] == null)
    }

    @Test
    fun `parses empty containers`() {
        assertEquals(JsonValue.Obj(), parse("{}"))
        assertEquals(JsonValue.Arr(), parse("[]"))
    }

    @Test
    fun `parses string escapes and unicode`() {
        assertEquals("line1\nline2", (parse("\"line1\\nline2\"") as JsonValue.Str).value)
        assertEquals("a\"b", (parse("\"a\\\"b\"") as JsonValue.Str).value)
        assertEquals("a\\b", (parse("\"a\\\\b\"") as JsonValue.Str).value)
        assertEquals("tab\there", (parse("\"tab\\there\"") as JsonValue.Str).value)
        assertEquals("A", (parse("\"\\u0041\"") as JsonValue.Str).value)
    }

    @Test
    fun `parse errors are thrown as JsonException`() {
        val cases = listOf("", "{", "[1,2", "\"unterminated", "tru", "1..2", "{\"a\":1} extra")
        for (case in cases) {
            try {
                Json.parse(case)
                throw AssertionError("expected JsonException for: $case")
            } catch (expected: JsonException) {
                // structured failure, never silent
            }
        }
    }

    @Test
    fun `write round trips a nested value`() {
        val original = Json.parse(
            """{"action":"CLICK","target_id":"n1","tags":["a","b"],"n":3,"ok":true,"none":null}""",
        )
        val written = Json.write(original)
        assertEquals(original, Json.parse(written))
    }

    @Test
    fun `write escapes control characters`() {
        val value = JsonValue.Obj(
            linkedMapOf("s" to JsonValue.Str("quote\" newline\n back\\ tab\t end")),
        )
        val written = Json.write(value)
        assertTrue(written.contains("\\\""))
        assertTrue(written.contains("\\n"))
        assertTrue(written.contains("\\\\"))
        assertTrue(written.contains("\\t"))
        assertEquals(value, Json.parse(written))
    }

    @Test
    fun `write renders whole doubles as integers`() {
        assertEquals("42", Json.write(JsonValue.Num(42.0)))
        assertEquals("3.5", Json.write(JsonValue.Num(3.5)))
        assertEquals("true", Json.write(JsonValue.Bool(true)))
        assertEquals("null", Json.write(JsonValue.Null))
        assertEquals("[1,2]", Json.write(JsonValue.Arr(listOf(JsonValue.Num(1.0), JsonValue.Num(2.0)))))
    }

    @Test
    fun `convenience readers return null on type mismatch`() {
        val obj = parse("""{"s":"v","n":1,"b":true}""") as JsonValue.Obj
        assertEquals("v", Json.run { obj.string("s") })
        assertEquals(true, Json.run { obj.boolean("b") })
        assertTrue(Json.run { obj.string("n") } == null)
        assertTrue(Json.run { obj.boolean("s") } == null)
        assertTrue(Json.run { obj.string("missing") } == null)
    }
}
