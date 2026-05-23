package llc.redstone.htslreborn.parser

import llc.redstone.htslreborn.tokenizer.Tokenizer
import llc.redstone.systemsdata.Action
import llc.redstone.systemsdata.Comparison
import llc.redstone.systemsdata.Condition
import llc.redstone.systemsdata.StatOp
import llc.redstone.systemsdata.StatValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.io.path.Path

class ParserTest {

    @Test
    fun testChangeVelocity() {
        val input = """
            changeVelocity %var.player/t% 1.0 0.0
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(1, actions.size, "Should have parsed exactly one action")

        val action = actions[0]
        // Assuming the toString representation is as expected by the user
        // Or we can check properties if we know the class structure
        assertEquals("ChangeVelocity(x=%var.player/t%, y=1.0, z=0.0)", action.toString())
    }

    @Test
    fun testMultipleActions() {
        val input = """
            changeVelocity 1.0 2.0 3.0
            changeVelocity %var.player/x% %var.player/y% %var.player/z%
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(2, actions.size)
        assertEquals("ChangeVelocity(x=1.0, y=2.0, z=3.0)", actions[0].toString())
        assertEquals("ChangeVelocity(x=%var.player/x%, y=%var.player/y%, z=%var.player/z%)", actions[1].toString())
    }

    @Test
    fun testVariableActionAliasesImport() {
        val input = """
            var Kills = 1
            stat Kills = 1
            globalvar Total += 2
            globalstat Total += 2
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(
            listOf(
                Action.PlayerVariable("Kills", StatOp.Set, StatValue.I32(1)),
                Action.PlayerVariable("Kills", StatOp.Set, StatValue.I32(1)),
                Action.GlobalVariable("Total", StatOp.Inc, StatValue.I32(2)),
                Action.GlobalVariable("Total", StatOp.Inc, StatValue.I32(2)),
            ),
            actions
        )
    }

    @Test
    fun testQuotedDynamicVariableValuesImportAsDynamic() {
        val input = """
            var "temp2" *= "%var.player/temp2 0.0%" false
            globalvar "ray/look/x" = "%var.player/temp2 0.0%D" false
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(
            listOf(
                Action.PlayerVariable("temp2", StatOp.Mul, StatValue.UnquotedStr("%var.player/temp2 0.0%"), false),
                Action.GlobalVariable("ray/look/x", StatOp.Set, StatValue.UnquotedStr("%var.player/temp2 0.0%"), false),
            ),
            actions
        )
    }

    @Test
    fun testVariableValueTypeSyntaxImport() {
        val input = """
            var whole = 500
            var decimal = 500.5
            var forcedLong = 500L
            var forcedDouble = 500D
            var quotedNumber = "500"
            var quotedText = "hello"
            var bareText = hello
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(
            listOf(
                Action.PlayerVariable("whole", StatOp.Set, StatValue.I32(500)),
                Action.PlayerVariable("decimal", StatOp.Set, StatValue.Dbl(500.5)),
                Action.PlayerVariable("forcedLong", StatOp.Set, StatValue.Lng(500)),
                Action.PlayerVariable("forcedDouble", StatOp.Set, StatValue.Dbl(500.0)),
                Action.PlayerVariable("quotedNumber", StatOp.Set, StatValue.Str("500")),
                Action.PlayerVariable("quotedText", StatOp.Set, StatValue.Str("hello")),
                Action.PlayerVariable("bareText", StatOp.Set, StatValue.UnquotedStr("hello")),
            ),
            actions
        )
    }

    @Test
    fun testVariableConditionAliasesImport() {
        val input = """
            if (var Kills == 1, stat Kills == 1, globalvar Total >= 2, globalstat Total >= 2) {
                chat ok
            }
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        val conditional = actions.single() as Action.Conditional

        assertEquals(
            listOf(
                Condition.PlayerVariableRequirement("Kills", Comparison.Eq, StatValue.I32(1)),
                Condition.PlayerVariableRequirement("Kills", Comparison.Eq, StatValue.I32(1)),
                Condition.GlobalVariableRequirement("Total", Comparison.Ge, StatValue.I32(2)),
                Condition.GlobalVariableRequirement("Total", Comparison.Ge, StatValue.I32(2)),
            ),
            conditional.conditions
        )
    }

    @Test
    fun testVariableConditionValueTypeSyntaxImport() {
        val input = """
            if (var quotedNumber == "500", var quotedText == "hello", var legacyDynamic == "%var.player/temp2 0.0%D", var percentText == "hello%world") {
                chat ok
            }
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        val conditional = actions.single() as Action.Conditional

        assertEquals(
            listOf(
                Condition.PlayerVariableRequirement("quotedNumber", Comparison.Eq, StatValue.Str("500")),
                Condition.PlayerVariableRequirement("quotedText", Comparison.Eq, StatValue.Str("hello")),
                Condition.PlayerVariableRequirement("legacyDynamic", Comparison.Eq, StatValue.UnquotedStr("%var.player/temp2 0.0%")),
                Condition.PlayerVariableRequirement("percentText", Comparison.Eq, StatValue.Str("hello%world")),
            ),
            conditional.conditions
        )
    }

    @Test
    fun testReadableConditionAliasesImport() {
        val input = """
            if (hasGroup default true, inGroup default true, hasTeam red, inTeam red, hasRegion spawn, inRegion spawn) {
                chat ok
            }
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        val conditional = actions.single() as Action.Conditional

        assertEquals(
            listOf(
                Condition.RequiredGroup("default", true),
                Condition.RequiredGroup("default", true),
                Condition.RequiredTeam("red"),
                Condition.RequiredTeam("red"),
                Condition.InRegion("spawn"),
                Condition.InRegion("spawn"),
            ),
            conditional.conditions
        )
    }

    @Test
    fun testActionBarKeepsStringValue() {
        val input = """
            actionBar "Kills: %stat.player/Kills%"
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(Action.DisplayActionBar("Kills: %stat.player/Kills%"), actions.single())
    }

    @Test
    fun testActionBarKeepsColorCodeJoinedToPlaceholder() {
        val input = """
            actionBar &e%stat.player/tpsdistv2%
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(Action.DisplayActionBar("&e%stat.player/tpsdistv2%"), actions.single())
    }

    @Test
    fun testActionBarCanImportLiteralNullText() {
        val input = """
            actionBar null
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(Action.DisplayActionBar("null"), actions.single())
    }

    @Test
    fun testChatMessageKeepsJoinPayloadPlaceholders() {
        val input = """
            chat type=join;name=%var.global/join/name%;playerid=%var.global/join/playerid%
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(
            Action.SendMessage("type=join;name=%var.global/join/name%;playerid=%var.global/join/playerid%"),
            actions.single()
        )
    }

    @Test
    fun testNumbersWithCommasParseAsNumbers() {
        val input = """
            pause 1,234
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(Action.PauseExecution(1234), actions.single())
    }

    @Test
    fun testStatValuesWithCommasParseAsNumbers() {
        val input = """
            changeVelocity 1,234 2,345L 3,456.5
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        assertEquals(
            Action.ChangeVelocity(
                StatValue.I32(1234),
                StatValue.Lng(2345),
                StatValue.Dbl(3456.5)
            ),
            actions.single()
        )
    }

    @Test
    fun testConditionKeepsColorCodeJoinedToPlaceholder() {
        val input = """
            if (placeholder &e%stat.player/tpsdistv2% >= 1,234) {
                chat ok
            }
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        val conditional = actions.single() as Action.Conditional
        val condition = conditional.conditions.single() as Condition.RequiredPlaceholderNumber

        assertEquals("&e%stat.player/tpsdistv2%", condition.placeholder)
        assertEquals(Comparison.Ge, condition.mode)
        assertEquals(StatValue.I32(1234), condition.amount)
    }

    @Test
    fun testCustomLocationDoesNotConsumeDefaultStrength() {
        val input = """
            launchTarget "custom_coordinates" 1 2 3
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        val action = actions.single() as Action.LaunchToTarget

        assertEquals("1 2 3", action.location.toString())
        assertEquals(2.0, action.strength)
    }

    @Test
    fun testNestedConditionalsKeepTheirOwnConditions() {
        val input = """
            if (isSneaking) {
                if (isFlying) {
                    chat nested
                }
            }
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        val outer = actions.single() as Action.Conditional
        val inner = outer.ifActions.single() as Action.Conditional

        assertTrue(outer.conditions.single() is Condition.PlayerSneaking)
        assertTrue(inner.conditions.single() is Condition.PlayerFlying)
    }

    @Test
    fun testInvertedConditionAfterComma() {
        val input = """
            if (isSneaking, !isFlying) {
                chat nope
            }
        """.trimIndent()

        val tokens = Tokenizer.tokenize(input)
        val preProcessedTokens = PreProcess.preProcess(tokens)
        val actions = Parser.parse(preProcessedTokens, Path("test.htsl")).toMap()["base"] ?: emptyList()

        val conditional = actions.single() as Action.Conditional

        assertTrue(conditional.conditions[0] is Condition.PlayerSneaking)
        assertTrue(conditional.conditions[1] is Condition.PlayerFlying)
        assertEquals(false, conditional.conditions[0].inverted)
        assertEquals(true, conditional.conditions[1].inverted)
    }
}
