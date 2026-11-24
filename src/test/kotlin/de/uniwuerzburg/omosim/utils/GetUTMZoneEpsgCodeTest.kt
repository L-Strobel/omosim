package de.uniwuerzburg.omosim.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GetUTMZoneEpsgCodeTest {

    @Test
    fun germany() {
        val lat = 48.44763061003003
        val lon = 11.147267282494916
        val code = getUTMZoneEpsgCode(lat, lon)
        assertEquals("EPSG:32632", code)
    }

    @Test
    fun madagascar() {
        val lat = -18.49510424098359
        val lon =  45.174444246717535
        val code = getUTMZoneEpsgCode(lat, lon)
        assertEquals("EPSG:32738", code)
    }

    @Test
    fun norway() {
        val lat = 59.193439710075864
        val lon = 4.8146067628726135
        val code = getUTMZoneEpsgCode(lat, lon)
        assertEquals("EPSG:32632", code)
    }

    @Test
    fun southPole() {
        val lat = -87.80042430026619
        val lon =  13.015853672547058
        val code = getUTMZoneEpsgCode(lat, lon)
        assertEquals("EPSG:32761", code)
    }
}