package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.utils.CRSTransformer
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.io.WKTReader
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stores traffic count data for calibration.
 *
 * @param name Name of the sensor
 * @param measurements Measured traffic for each time step
 * @param direction Direction in which the sensor counts traffic
 * @param fov Field of view of the sensor. Sensor counts all traffic in the fov that is facing its direction.
 */
class TrafficSensor(
    val name: String,
    val measurements: DoubleArray,
    val direction: Direction?,
    val fov: Geometry
) {
    companion object {
        /**
         * Read calibration input file.
         *
         * @param file File
         * @param transformer Coordinate system transformer
         */
        fun readSensorData(
            file: File,
            transformer: CRSTransformer,
            delimiter: String = ";"
        ): List<TrafficSensor> {
            val sensors = mutableListOf<TrafficSensor>()
            val reader = file.bufferedReader()
            val wktReader = WKTReader()

            // Parse header
            val header = reader.readLine()
            val idxMap = header
                .split(delimiter)
                .map{ it.lowercase() }
                .withIndex()
                .associate { (i, v) -> v to i }

            // Index of cols to extract
            val nameCol = idxMap["name"]
            val flowCol = idxMap["counts"]
            val dirCol = idxMap["direction"]
            val geometryCol = idxMap["geometry"]

            // Read data
            for (line in reader.lines()) {
                val values = line.split(delimiter)

                // Name
                val name = nameCol?.let { values[it] } ?: sensors.size.toString()

                // Measurements
                val measurements = values[flowCol!!]
                    .replace("[", "")
                    .replace("]", "")
                    .split(",")
                    .map { it.toDouble() }
                    .toDoubleArray()

                // Field of view
                val wkt = values[geometryCol!!]
                val latlonGeom = wktReader.read(wkt)
                val fov = transformer.toModelCRS(latlonGeom)

                // Direction
                val angle = values[dirCol!!].toDoubleOrNull()
                val centroid = latlonGeom.centroid.coordinates.first()
                val direction = angle?.let { Direction(it, centroid, transformer) }

                val sensor = TrafficSensor(name, measurements, direction, fov)
                sensors.add(sensor)
            }
            reader.close()
            return sensors
        }
    }

    fun isInMeasurementDirection(route: LineString, leeway: Double) : Boolean {
        return if (this.direction == null) {
            true
        } else {
            this.direction.isSameDirection(route, leeway)
        }
    }

    /**
     * Direction in which a sensor records traffic.
     *
     * @param azimuth angle. 0°: North,  90°: East,  180°: South, 270°: West. Unit: Degrees
     * @param center Location of the sensor. Point.
     * @param transformer Coordinate system transformer
     */
    class Direction(
        azimuth: Double,
        center: Coordinate,
        transformer: CRSTransformer
    ) {
        private val vector: DoubleArray // 2D vector that represents the direction

        init {
            // Convert angle
            var polarAngle = 360.0 - azimuth + 90.0
            if (polarAngle >= 360.0) {
                polarAngle -= 360.0
            }

            // Create vector
            val dx = 0.01 * cos(polarAngle / 180.0 * PI)
            val dy = 0.01 * sin(polarAngle / 180.0 * PI)
            val vCoordsLatLon = GeometryFactory().createLineString(
                listOf(
                    center,
                    Coordinate(center.x + dy, center.y + dx) // Swap dx and dy because of lat-lon crs
                ).toTypedArray()
            )

            val vCoordsUTM = transformer
                .toModelCRS(vCoordsLatLon)
                .coordinates

            vector = vecFromCoords(vCoordsUTM)
        }

        /**
         * Create a 2D vector between two coordinates.
         *
         * @param coords Array that contains exactly two coordinates, A and B.
         * @return vector from A to B
         */
        private fun vecFromCoords(coords: Array<Coordinate>) : DoubleArray {
            require(coords.size == 2)

            val vector = DoubleArray(2) { 0.0 }
            vector[0] = coords[1].x - coords[0].x
            vector[1] = coords[1].y - coords[0].y
            return vector
        }

        /**
         * Determine angle between a LineString and the direction.
         *
         * @param other LineString
         * @return Angle
         */
        private fun angleBetween(other: LineString): Double {
            val oVec = vecFromCoords(arrayOf(other.coordinates.first(), other.coordinates.last()))

            val dot = vector[0] * oVec[0] + vector[1] * oVec[1]
            val det = vector[0] * oVec[1] - vector[1] * oVec[0]
            return atan2(det, dot)
        }

        /**
         * Check if a LineString faces in the given direction given a certain leeway.
         *
         * @param other LineString
         * @param leeway Angle leeway. In degrees.
         */
        fun isSameDirection(other: LineString, leeway: Double) : Boolean {
            val leewayRad = leeway / 180.0 * PI
            return abs(angleBetween(other)) < leewayRad
        }
    }
}