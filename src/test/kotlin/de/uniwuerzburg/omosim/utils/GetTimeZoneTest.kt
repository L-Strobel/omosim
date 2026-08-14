package de.uniwuerzburg.omosim.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import java.util.TimeZone

class GetTimeZoneTest {
    val geometryFactory = GeometryFactory()

    @Test
    fun getTimeZoneWuerzburg() {
        val coord = Coordinate(49.78182741518254, 9.969307782806993) // University of Würzburg
        val area = geometryFactory.createPoint(coord).buffer(0.1)
        val actualTimeZone = getTimeZone(area)
        val expectedTimeZone = TimeZone.getTimeZone("Europe/Berlin")
        assertEquals(expectedTimeZone, actualTimeZone)
    }

    @Test
    fun getTimeZoneSantiagoDeChile() {
        val coord = Coordinate(-33.46401084638548, -70.64080900640978) // University of Würzburg
        val area = geometryFactory.createPoint(coord).buffer(0.1)
        val actualTimeZone = getTimeZone(area)
        val expectedTimeZone = TimeZone.getTimeZone("America/Santiago")
        assertEquals(expectedTimeZone, actualTimeZone)
    }
}