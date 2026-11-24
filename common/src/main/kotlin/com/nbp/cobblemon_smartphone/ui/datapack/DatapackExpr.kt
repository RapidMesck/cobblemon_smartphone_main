package com.nbp.cobblemon_smartphone.ui.datapack

/**
 * Expression evaluator, template resolver, and parameter sanitizer for datapack-defined HD screens.
 */
object DatapackExpr {

    data class EvalContext(
        val placeholders: Map<String, String> = emptyMap(),
        val lists: Map<String, List<Map<String, String>>> = emptyMap(),
        val state: Map<String, String> = emptyMap(),
        val inputs: Map<String, String> = emptyMap(),
        val item: Map<String, String> = emptyMap()
    )

    private val PLACEHOLDER_REGEX = Regex("\\{([a-zA-Z0-9_.:-]+)}")
    private val UNSAFE_COMMAND_INPUT_REGEX = Regex("[^A-Za-z0-9_.\\- ]")

    /**
     * Resolves `{key}`, `{state.x}`, `{inputs.x}`, `{item.x}`, and `{placeholder.x}` tokens.
     */
    fun resolveTemplate(template: String, ctx: EvalContext): String {
        if ('{' !in template) return template
        return PLACEHOLDER_REGEX.replace(template) { match ->
            lookupVariable(match.groupValues[1], ctx) ?: ""
        }
    }

    /**
     * Sanitizes client-supplied text before interpolating into a server command so players
     * cannot inject target selectors (`@a`/`@e`), command separators, or slash prefixes.
     */
    fun sanitizeInputForCommand(raw: String, maxLength: Int = 48): String {
        val cleaned = raw
            .replace("\r", "")
            .replace("\n", "")
            .replace("@", "")
            .replace("/", "")
            .replace("$", "")
        return UNSAFE_COMMAND_INPUT_REGEX.replace(cleaned, "").trim().take(maxLength)
    }

    fun lookupVariable(rawKey: String, ctx: EvalContext): String? {
        val key = rawKey.trim()
        if (key.endsWith(".size")) {
            val base = key.removeSuffix(".size")
            val list = when {
                base.startsWith("lists.") -> ctx.lists[base.removePrefix("lists.")]
                base.startsWith("list.") -> ctx.lists[base.removePrefix("list.")]
                else -> ctx.lists[base]
            }
            if (list != null) return list.size.toString()
            return (lookupVariable(base, ctx)?.length ?: 0).toString()
        }
        if (key.endsWith(".empty")) {
            val base = key.removeSuffix(".empty")
            val list = when {
                base.startsWith("lists.") -> ctx.lists[base.removePrefix("lists.")]
                base.startsWith("list.") -> ctx.lists[base.removePrefix("list.")]
                else -> ctx.lists[base]
            }
            if (list != null) return list.isEmpty().toString()
            return lookupVariable(base, ctx).isNullOrEmpty().toString()
        }
        return when {
            key.startsWith("state.") -> ctx.state[key.removePrefix("state.")]
            key.startsWith("inputs.") -> ctx.inputs[key.removePrefix("inputs.")]
            key.startsWith("input.") -> ctx.inputs[key.removePrefix("input.")]
            key.startsWith("item.") -> ctx.item[key.removePrefix("item.")]
            key.startsWith("placeholder.") -> ctx.placeholders[key.removePrefix("placeholder.")]
            key.startsWith("placeholders.") -> ctx.placeholders[key.removePrefix("placeholders.")]
            else -> ctx.item[key] ?: ctx.state[key] ?: ctx.inputs[key] ?: ctx.placeholders[key]
        }
    }

    /**
     * Evaluates a boolean `visible_if` / condition expression. Returns `true` when [expr] is null or blank.
     */
    fun evaluateCondition(expr: String?, ctx: EvalContext): Boolean {
        if (expr.isNullOrBlank()) return true
        val tokens = tokenize(expr)
        if (tokens.isEmpty()) return true
        val parser = Parser(tokens, ctx)
        val result = parser.parseOr()
        return asBoolean(result)
    }

    private sealed interface Token {
        data class Ident(val text: String) : Token
        data class Str(val text: String) : Token
        data class Num(val value: Double) : Token
        data class Bool(val value: Boolean) : Token
        data class Op(val text: String) : Token
        data object LParen : Token
        data object RParen : Token
    }

    private fun tokenize(input: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                ch.isWhitespace() -> i++
                ch == '(' -> { out.add(Token.LParen); i++ }
                ch == ')' -> { out.add(Token.RParen); i++ }
                ch == '\'' || ch == '"' -> {
                    val quote = ch
                    i++
                    val sb = StringBuilder()
                    while (i < input.length && input[i] != quote) {
                        if (input[i] == '\\' && i + 1 < input.length) {
                            i++
                        }
                        sb.append(input[i])
                        i++
                    }
                    if (i < input.length && input[i] == quote) i++
                    out.add(Token.Str(sb.toString()))
                }
                i + 1 < input.length && input.substring(i, i + 2) in TWO_CHAR_OPS -> {
                    out.add(Token.Op(input.substring(i, i + 2)))
                    i += 2
                }
                ch == '!' || ch == '<' || ch == '>' -> {
                    out.add(Token.Op(ch.toString()))
                    i++
                }
                ch.isDigit() || (ch == '-' && i + 1 < input.length && input[i + 1].isDigit() &&
                    (out.isEmpty() || out.last() is Token.Op || out.last() == Token.LParen)) -> {
                    val start = i
                    i++
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    val num = input.substring(start, i).toDoubleOrNull() ?: 0.0
                    out.add(Token.Num(num))
                }
                isIdentChar(ch) -> {
                    val start = i
                    while (i < input.length && isIdentChar(input[i])) i++
                    val word = input.substring(start, i)
                    when {
                        word.equals("true", ignoreCase = true) -> out.add(Token.Bool(true))
                        word.equals("false", ignoreCase = true) -> out.add(Token.Bool(false))
                        word.equals("and", ignoreCase = true) -> out.add(Token.Op("&&"))
                        word.equals("or", ignoreCase = true) -> out.add(Token.Op("||"))
                        word.equals("not", ignoreCase = true) -> out.add(Token.Op("!"))
                        else -> out.add(Token.Ident(word))
                    }
                }
                else -> i++
            }
        }
        return out
    }

    private fun isIdentChar(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == ':' || ch == '-'

    private class Parser(
        private val tokens: List<Token>,
        private val ctx: EvalContext
    ) {
        private var pos = 0

        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun next(): Token = tokens[pos++]

        fun parseOr(): Any {
            var left = parseAnd()
            while (peek() == Token.Op("||")) {
                next()
                val right = parseAnd()
                left = asBoolean(left) || asBoolean(right)
            }
            return left
        }

        private fun parseAnd(): Any {
            var left = parseComparison()
            while (peek() == Token.Op("&&")) {
                next()
                val right = parseComparison()
                left = asBoolean(left) && asBoolean(right)
            }
            return left
        }

        private fun parseComparison(): Any {
            val left = parseUnary()
            val op = peek() as? Token.Op ?: return left
            if (op.text !in COMPARISON_OPS) return left
            next()
            val right = parseUnary()
            return compareValues(left, right, op.text)
        }

        private fun parseUnary(): Any {
            if (peek() == Token.Op("!")) {
                next()
                return !asBoolean(parseUnary())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Any {
            val tok = peek() ?: return ""
            return when (tok) {
                Token.LParen -> {
                    next()
                    val v = parseOr()
                    if (peek() == Token.RParen) next()
                    v
                }
                is Token.Bool -> { next(); tok.value }
                is Token.Num -> { next(); tok.value }
                is Token.Str -> { next(); tok.text }
                is Token.Ident -> {
                    next()
                    resolveIdent(tok.text)
                }
                else -> { next(); "" }
            }
        }

        private fun resolveIdent(ident: String): String {
            if (ident.endsWith(".empty")) {
                val base = ident.removeSuffix(".empty")
                val list = resolveList(base)
                if (list != null) return list.isEmpty().toString()
                return (lookupVariable(base, ctx).isNullOrEmpty()).toString()
            }
            if (ident.endsWith(".not_empty")) {
                val base = ident.removeSuffix(".not_empty")
                val list = resolveList(base)
                if (list != null) return list.isNotEmpty().toString()
                return (!lookupVariable(base, ctx).isNullOrEmpty()).toString()
            }
            if (ident.endsWith(".size")) {
                val base = ident.removeSuffix(".size")
                val list = resolveList(base)
                if (list != null) return list.size.toString()
                return (lookupVariable(base, ctx)?.length ?: 0).toString()
            }
            return lookupVariable(ident, ctx) ?: ""
        }

        private fun resolveList(key: String): List<Map<String, String>>? = when {
            key.startsWith("lists.") -> ctx.lists[key.removePrefix("lists.")]
            key.startsWith("list.") -> ctx.lists[key.removePrefix("list.")]
            else -> ctx.lists[key]
        }
    }

    private fun compareValues(left: Any, right: Any, op: String): Boolean {
        val lNum = asDoubleOrNull(left)
        val rNum = asDoubleOrNull(right)
        if (lNum != null && rNum != null) {
            return when (op) {
                "==" -> lNum == rNum
                "!=" -> lNum != rNum
                ">" -> lNum > rNum
                ">=" -> lNum >= rNum
                "<" -> lNum < rNum
                "<=" -> lNum <= rNum
                else -> false
            }
        }
        val lStr = asString(left)
        val rStr = asString(right)
        return when (op) {
            "==" -> lStr.equals(rStr, ignoreCase = false)
            "!=" -> !lStr.equals(rStr, ignoreCase = false)
            "~=" -> lStr.contains(rStr, ignoreCase = true)
            ">" -> lStr > rStr
            ">=" -> lStr >= rStr
            "<" -> lStr < rStr
            "<=" -> lStr <= rStr
            else -> false
        }
    }

    private fun asDoubleOrNull(v: Any): Double? = when (v) {
        is Double -> v
        is Number -> v.toDouble()
        is Boolean -> if (v) 1.0 else 0.0
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }

    private fun asString(v: Any): String = when (v) {
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    private fun asBoolean(v: Any): Boolean = when (v) {
        is Boolean -> v
        is Double -> v != 0.0
        is String -> {
            val trimmed = v.trim()
            trimmed.isNotEmpty() &&
                !trimmed.equals("false", ignoreCase = true) &&
                trimmed != "0" &&
                !trimmed.equals("null", ignoreCase = true)
        }
        else -> true
    }

    private val TWO_CHAR_OPS = setOf("==", "!=", ">=", "<=", "&&", "||", "~=")
    private val COMPARISON_OPS = setOf("==", "!=", ">", ">=", "<", "<=", "~=")
}
