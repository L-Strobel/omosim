package de.uniwuerzburg.omosim.io

import de.uniwuerzburg.omosim.core.models.HomogeneousGrp
import de.uniwuerzburg.omosim.core.models.MobilityGrp
import de.uniwuerzburg.omosim.core.models.Sex
import de.uniwuerzburg.omosim.io.json.OutputDiary
import de.uniwuerzburg.omosim.io.json.OutputEntry
import de.uniwuerzburg.omosim.io.sqlite.writeSQLite
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.ResultSet
import kotlin.io.path.absolutePathString

class WriteSQLiteTest {

    @TempDir
    lateinit var tmp: Path

    private fun <T> query(file: Path, sql: String, block: (ResultSet) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePathString()}").use { conn ->
            conn.createStatement().use { st -> st.executeQuery(sql).use(block) }
        }

    private fun makeEntry(
        id: Int = 1,
        homogenousGroup: HomogeneousGrp = HomogeneousGrp.UNDEFINED,
        mobilityGroup: MobilityGrp = MobilityGrp.UNDEFINED,
        age: Int? = 30,
        monthlyIncome: Int? = 2000,
        sex: Sex = Sex.UNDEFINED,
        carAccess: Boolean = true,
        homeLat: Double = 49.781,
        homeLon: Double = 9.973,
        homeDummyLoc: Boolean = false,
        homeInFocus: Boolean = true,
        mobilityDemand: List<OutputDiary> = emptyList()
    ) = OutputEntry(
        id = id,
        homogenousGroup = homogenousGroup,
        mobilityGroup = mobilityGroup,
        age = age,
        monthlyIncome = monthlyIncome,
        sex = sex,
        carAccess = carAccess,
        homeLat = homeLat,
        homeLon = homeLon,
        homeDummyLoc = homeDummyLoc,
        homeInFocus = homeInFocus,
        mobilityDemand = mobilityDemand
    )


    @Test
    fun `person with undefined age and income is stored as NULL`() {
        val file = tmp.resolve("test.db")
        val entries = listOf(
            makeEntry(id = 1, age = null, monthlyIncome = null)
        )

        assert(writeSQLite(entries, file, mapOf("seed" to "42")))

        query(file, "SELECT age, monthlyIncome FROM person WHERE id = 1") { rs ->
            assert(rs.getObject("age") == null)
            assert(rs.getObject("monthlyIncome") == null)
        }
    }

    @Test
    fun `person with age and income defined`() {
        val file = tmp.resolve("test.db")
        val entries = listOf(
            makeEntry(id = 1, monthlyIncome = 2400)
        )

        assert(writeSQLite(entries, file, mapOf("seed" to "42")))

        query(file, "SELECT age, monthlyIncome FROM person WHERE id = 1") { rs ->
            assert(rs.getObject("age") == 30)
            assert(rs.getObject("monthlyIncome") == 2400)
        }
    }

}