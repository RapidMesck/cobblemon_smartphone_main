package com.nbp.cobblemon_smartphone.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Pure, UI-independent calculator state machine backed by [BigDecimal].
 * Designed for smartphone-style interactive input, chained operations, repeated `=`,
 * percentage calculations, sign toggling, backspace, and calculation history.
 */
class CalculatorEngine {

    enum class Operator(val symbol: String) {
        ADD("+"),
        SUBTRACT("−"),
        MULTIPLY("×"),
        DIVIDE("÷")
    }

    data class HistoryEntry(
        val expression: String,
        val result: String
    )

    private var currentInput: String = "0"
    private var accumulator: BigDecimal? = null
    private var pendingOperator: Operator? = null
    private var lastOperator: Operator? = null
    private var lastOperand: BigDecimal? = null
    private var waitingForOperand: Boolean = false
    private var expressionPreview: String = ""
    private var hasError: Boolean = false
    private val _history = mutableListOf<HistoryEntry>()

    val displayText: String
        get() = if (hasError) ERROR_TOKEN else currentInput

    val expressionText: String
        get() = expressionPreview

    val isError: Boolean
        get() = hasError

    val activeOperator: Operator?
        get() = if (waitingForOperand && !hasError) pendingOperator else null

    val canClearEntryOnly: Boolean
        get() = !hasError && !waitingForOperand && currentInput != "0"

    val history: List<HistoryEntry>
        get() = _history.toList()

    fun inputDigit(digit: Char) {
        if (digit !in '0'..'9') return
        if (hasError) {
            clearAll()
        }
        if (waitingForOperand) {
            currentInput = digit.toString()
            waitingForOperand = false
            return
        }
        if (currentInput == "0") {
            currentInput = digit.toString()
            return
        }
        if (currentInput == "-0") {
            currentInput = "-$digit"
            return
        }
        val digitCount = currentInput.count { it.isDigit() }
        if (digitCount >= MAX_INPUT_DIGITS) return
        currentInput += digit
    }

    fun inputDecimal() {
        if (hasError) {
            clearAll()
        }
        if (waitingForOperand) {
            currentInput = "0."
            waitingForOperand = false
            return
        }
        if ('.' !in currentInput) {
            currentInput += "."
        }
    }

    fun inputOperator(op: Operator) {
        if (hasError) return

        val inputVal = parseCurrent() ?: return
        if (accumulator == null) {
            accumulator = inputVal
        } else if (pendingOperator != null && !waitingForOperand) {
            val result = compute(accumulator!!, inputVal, pendingOperator!!)
            if (result == null) {
                triggerError()
                return
            }
            accumulator = result
            currentInput = formatNumber(result)
        }

        pendingOperator = op
        waitingForOperand = true
        lastOperator = null
        lastOperand = null
        expressionPreview = "${formatNumber(accumulator!!)} ${op.symbol}"
    }

    fun evaluate() {
        if (hasError) return

        val currentVal = parseCurrent() ?: return

        val op: Operator
        val left: BigDecimal
        val right: BigDecimal

        if (pendingOperator != null && accumulator != null) {
            op = pendingOperator!!
            left = accumulator!!
            right = currentVal
            lastOperator = op
            lastOperand = right
            pendingOperator = null
        } else if (lastOperator != null && lastOperand != null) {
            op = lastOperator!!
            left = currentVal
            right = lastOperand!!
        } else {
            return
        }

        val result = compute(left, right, op)
        if (result == null) {
            triggerError()
            return
        }

        val formattedLeft = formatNumber(left)
        val formattedRight = formatNumber(right)
        val formattedResult = formatNumber(result)
        val expr = "$formattedLeft ${op.symbol} $formattedRight ="

        expressionPreview = expr
        currentInput = formattedResult
        accumulator = null
        waitingForOperand = true

        _history.add(0, HistoryEntry(expr.removeSuffix(" ="), formattedResult))
        if (_history.size > MAX_HISTORY) {
            _history.removeAt(_history.lastIndex)
        }
    }

    fun toggleSign() {
        if (hasError) return
        if (currentInput == "0" || currentInput == "0.") return
        currentInput = if (currentInput.startsWith("-")) {
            currentInput.removePrefix("-")
        } else {
            "-$currentInput"
        }
        if (waitingForOperand && pendingOperator == null) {
            waitingForOperand = false
        }
    }

    fun percent() {
        if (hasError) return
        val currentVal = parseCurrent() ?: return
        val hundred = BigDecimal(100)
        val result = if (accumulator != null && (pendingOperator == Operator.ADD || pendingOperator == Operator.SUBTRACT)) {
            accumulator!!.multiply(currentVal, MATH_CONTEXT).divide(hundred, MATH_CONTEXT)
        } else {
            currentVal.divide(hundred, MATH_CONTEXT)
        }
        currentInput = formatNumber(result)
        waitingForOperand = false
    }

    fun backspace() {
        if (hasError) {
            clearAll()
            return
        }
        if (waitingForOperand) return
        currentInput = when {
            currentInput.length <= 1 -> "0"
            currentInput.length == 2 && currentInput.startsWith("-") -> "0"
            else -> currentInput.dropLast(1)
        }
    }

    fun clearSmart() {
        if (canClearEntryOnly) {
            currentInput = "0"
        } else {
            clearAll()
        }
    }

    fun clearAll() {
        currentInput = "0"
        accumulator = null
        pendingOperator = null
        lastOperator = null
        lastOperand = null
        waitingForOperand = false
        expressionPreview = ""
        hasError = false
    }

    fun recallValue(value: String) {
        val parsed = value.toBigDecimalOrNull() ?: return
        if (hasError) {
            clearAll()
        }
        currentInput = formatNumber(parsed)
        waitingForOperand = false
    }

    fun clearHistory() {
        _history.clear()
    }

    private fun triggerError() {
        hasError = true
        accumulator = null
        pendingOperator = null
        lastOperator = null
        lastOperand = null
        waitingForOperand = true
    }

    private fun parseCurrent(): BigDecimal? = currentInput.toBigDecimalOrNull()

    private fun compute(left: BigDecimal, right: BigDecimal, op: Operator): BigDecimal? {
        return try {
            when (op) {
                Operator.ADD -> left.add(right, MATH_CONTEXT)
                Operator.SUBTRACT -> left.subtract(right, MATH_CONTEXT)
                Operator.MULTIPLY -> left.multiply(right, MATH_CONTEXT)
                Operator.DIVIDE -> {
                    if (right.compareTo(BigDecimal.ZERO) == 0) return null
                    left.divide(right, MATH_CONTEXT)
                }
            }
        } catch (_: ArithmeticException) {
            null
        }
    }

    companion object {
        const val ERROR_TOKEN = "ERROR"
        private const val MAX_INPUT_DIGITS = 12
        private const val MAX_HISTORY = 25
        private val MATH_CONTEXT = MathContext(12, RoundingMode.HALF_UP)
        private val MAX_PLAIN = BigDecimal("999999999999")
        private val MIN_PLAIN = BigDecimal("0.0000001")

        fun formatNumber(value: BigDecimal): String {
            if (value.compareTo(BigDecimal.ZERO) == 0) return "0"
            val stripped = value.round(MATH_CONTEXT).stripTrailingZeros()
            val absVal = stripped.abs()
            return if (absVal > MAX_PLAIN || (absVal > BigDecimal.ZERO && absVal < MIN_PLAIN)) {
                stripped.toEngineeringString()
            } else {
                stripped.toPlainString()
            }
        }
    }
}
