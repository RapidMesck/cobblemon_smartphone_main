package com.nbp.cobblemon_smartphone.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalculatorEngineTest {

    @Test
    fun `basic arithmetic and decimal precision`() {
        val calc = CalculatorEngine()
        calc.inputDigit('0')
        calc.inputDecimal()
        calc.inputDigit('1')
        calc.inputOperator(CalculatorEngine.Operator.ADD)
        calc.inputDigit('0')
        calc.inputDecimal()
        calc.inputDigit('2')
        calc.evaluate()

        assertEquals("0.3", calc.displayText)
        assertEquals("0.1 + 0.2 =", calc.expressionText)
        assertEquals(1, calc.history.size)
        assertEquals("0.3", calc.history.first().result)
    }

    @Test
    fun `chained operations evaluate left to right`() {
        val calc = CalculatorEngine()
        calc.inputDigit('6')
        calc.inputDigit('4')
        calc.inputOperator(CalculatorEngine.Operator.MULTIPLY)
        calc.inputDigit('2')
        calc.inputOperator(CalculatorEngine.Operator.ADD)
        assertEquals("128", calc.displayText)
        calc.inputDigit('8')
        calc.evaluate()

        assertEquals("136", calc.displayText)
    }

    @Test
    fun `repeated equals repeats last operation`() {
        val calc = CalculatorEngine()
        calc.inputDigit('5')
        calc.inputOperator(CalculatorEngine.Operator.ADD)
        calc.inputDigit('3')
        calc.evaluate()
        assertEquals("8", calc.displayText)

        calc.evaluate()
        assertEquals("11", calc.displayText)
        calc.evaluate()
        assertEquals("14", calc.displayText)
    }

    @Test
    fun `division by zero triggers error and recovers on next digit`() {
        val calc = CalculatorEngine()
        calc.inputDigit('9')
        calc.inputOperator(CalculatorEngine.Operator.DIVIDE)
        calc.inputDigit('0')
        calc.evaluate()

        assertTrue(calc.isError)
        assertEquals(CalculatorEngine.ERROR_TOKEN, calc.displayText)

        calc.inputDigit('4')
        assertFalse(calc.isError)
        assertEquals("4", calc.displayText)
    }

    @Test
    fun `percent, sign toggle, backspace, smart clear and history recall`() {
        val calc = CalculatorEngine()
        calc.inputDigit('2')
        calc.inputDigit('0')
        calc.inputDigit('0')
        calc.inputOperator(CalculatorEngine.Operator.ADD)
        calc.inputDigit('1')
        calc.inputDigit('0')
        calc.percent()
        assertEquals("20", calc.displayText)
        calc.evaluate()
        assertEquals("220", calc.displayText)

        calc.toggleSign()
        assertEquals("-220", calc.displayText)

        calc.backspace()
        assertEquals("-22", calc.displayText)

        calc.clearSmart()
        assertEquals("0", calc.displayText)

        calc.recallValue(calc.history.first().result)
        assertEquals("220", calc.displayText)
    }
}
