package llc.redstone.htslreborn.htslio

import llc.redstone.systemsdata.Action
import llc.redstone.systemsdata.Comparison
import llc.redstone.systemsdata.Condition
import llc.redstone.systemsdata.Location
import llc.redstone.systemsdata.StatOp
import llc.redstone.systemsdata.StatValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HTSLExporterTest {
    @Test
    fun testStringValuesAreQuotedWhenRequired() {
        val exported = HTSLExporter.export(
            listOf(
                Action.SendMessage("hello world"),
                Action.SendMessage(""),
                Action.SendMessage("null"),
                Action.SendMessage("type=join;name=%var.global/join/name%;playerid=%var.global/join/playerid%"),
            )
        )

        assertEquals(
            listOf(
                "chat \"hello world\"",
                "chat \"\"",
                "chat \"null\"",
                "chat type=join;name=%var.global/join/name%;playerid=%var.global/join/playerid%",
            ),
            exported
        )
    }

    @Test
    fun testVariableAliasesExportAsVarAndPreservePlaceholders() {
        val exportedActions = HTSLExporter.export(
            listOf(
                Action.PlayerVariable("Kills", StatOp.Set, StatValue.UnquotedStr("%stat.player/Kills%")),
                Action.PlayerVariable("temp2", StatOp.Mul, StatValue.UnquotedStr("%var.player/temp2 0.0%")),
                Action.PlayerVariable("legacy", StatOp.Set, StatValue.UnquotedStr("%var.player/temp2 0.0%D")),
                Action.GlobalVariable("Total", StatOp.Inc, StatValue.I32(1)),
            )
        )

        assertEquals(
            listOf(
                "var Kills = %stat.player/Kills% false",
                "var temp2 *= %var.player/temp2 0.0% false",
                "var legacy = %var.player/temp2 0.0% false",
                "globalvar Total += 1 false",
            ),
            exportedActions
        )

        val exportedConditions = HTSLExporter.exportConditions(
            listOf(
                Condition.PlayerVariableRequirement("Kills", Comparison.Ge, StatValue.UnquotedStr("%stat.player/Kills%")),
                Condition.PlayerVariableRequirement("temp2", Comparison.Eq, StatValue.UnquotedStr("%var.player/temp2 0.0%")),
                Condition.PlayerVariableRequirement("legacy", Comparison.Eq, StatValue.UnquotedStr("%var.player/temp2 0.0%D")),
                Condition.GlobalVariableRequirement("Total", Comparison.Eq, StatValue.I32(1)),
            )
        )

        assertEquals(
            listOf(
                "var Kills >= %stat.player/Kills% null",
                "var temp2 == %var.player/temp2 0.0% null",
                "var legacy == %var.player/temp2 0.0% null",
                "globalvar Total == 1 null",
            ),
            exportedConditions
        )
    }

    @Test
    fun testVariableValueTypeSyntaxExport() {
        val exported = HTSLExporter.export(
            listOf(
                Action.PlayerVariable("whole", StatOp.Set, StatValue.I32(500)),
                Action.PlayerVariable("decimal", StatOp.Set, StatValue.Dbl(500.5)),
                Action.PlayerVariable("forcedLong", StatOp.Set, StatValue.Lng(500)),
                Action.PlayerVariable("forcedDouble", StatOp.Set, StatValue.Dbl(500.0)),
                Action.PlayerVariable("quotedNumber", StatOp.Set, StatValue.Str("500")),
                Action.PlayerVariable("quotedText", StatOp.Set, StatValue.Str("hello")),
                Action.PlayerVariable("bareText", StatOp.Set, StatValue.UnquotedStr("hello")),
            )
        )

        assertEquals(
            listOf(
                "var whole = 500 false",
                "var decimal = 500.5 false",
                "var forcedLong = 500L false",
                "var forcedDouble = 500.0 false",
                "var quotedNumber = \"500\" false",
                "var quotedText = \"hello\" false",
                "var bareText = hello false",
            ),
            exported
        )
    }

    @Test
    fun testReadableConditionAliasesArePreferredOnExport() {
        val exportedConditions = HTSLExporter.exportConditions(
            listOf(
                Condition.RequiredGroup("default", true),
                Condition.RequiredTeam("red"),
                Condition.InRegion("spawn"),
            )
        )

        assertEquals(
            listOf(
                "hasGroup default true",
                "hasTeam red",
                "inRegion spawn",
            ),
            exportedConditions
        )
    }

    @Test
    fun testCustomLocationsExportAllCoordinates() {
        val exported = HTSLExporter.export(
            listOf(
                Action.TeleportPlayer(
                    Location.Custom(
                        Location.Custom.Coordinate("1"),
                        Location.Custom.Coordinate("2"),
                        Location.Custom.Coordinate("3"),
                    )
                )
            )
        )

        assertEquals(
            listOf("tp \"custom_coordinates\" \"1 2 3\" false"),
            exported
        )
    }
}
