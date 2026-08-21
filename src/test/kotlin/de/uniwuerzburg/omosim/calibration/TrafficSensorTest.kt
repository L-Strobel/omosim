package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.io.geojson.readGeoJsonGeom
import de.uniwuerzburg.omosim.utils.CRSTransformer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import java.nio.file.Path
import java.nio.file.Paths

class TrafficSensorTest {
    val geometryFactory = GeometryFactory()
    val areaFile: Path = Paths.get(Omosim::class.java.classLoader.getResource("smallTown/boundary.geojson")!!.toURI())
    val area = readGeoJsonGeom(areaFile, geometryFactory).union()
    val transformer = CRSTransformer(area.centroid.coordinate.y)
    val utmArea = transformer.toModelCRS(area)

    @Test
    fun readSensorData1MeasurementTest() {
        val sensorFile: Path = Paths.get(Omosim::class.java.classLoader.getResource("smallTown/sensor1Measurement.txt")!!.toURI())
        val sensors = TrafficSensor.readSensorData(sensorFile, transformer)

        assertEquals(1, sensors.size)
        assertEquals("sensor_1", sensors.first().name)
        assertEquals(1, sensors.first().measurements.size)
        assertTrue(sensors.all { it.fov.intersects(utmArea) })
    }

    @Test
    fun readSensorData4MeasurementTest() {
        val sensorFile: Path = Paths.get(Omosim::class.java.classLoader.getResource("smallTown/sensor4Measurements.txt")!!.toURI())
        val sensors = TrafficSensor.readSensorData(sensorFile, transformer)

        assertEquals(1, sensors.size)
        assertEquals("sensor_1", sensors.first().name)
        assertEquals(4, sensors.first().measurements.size)
        assertTrue(sensors.all { it.fov.intersects(utmArea) })
    }

    @Test
    fun isMeasurementDirectionTest() {
        val sensorFile: Path = Paths.get(Omosim::class.java.classLoader.getResource("smallTown/sensor1Measurement.txt")!!.toURI())
        val sensor = TrafficSensor.readSensorData(sensorFile, transformer).first()
        val route = geometryFactory.createLineString(
            arrayOf(
                Coordinate(47.996803, 11.3386341),
                Coordinate( 47.9969007, 11.3389063),
            )
        )
        val utmRoute = transformer.toModelCRS(route) as LineString

        assertNotNull(sensor.direction)
        assertTrue { sensor.isInMeasurementDirection(utmRoute, 30.0) }
    }
}