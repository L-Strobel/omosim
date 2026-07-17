package de.uniwuerzburg.omosim.calibration

import de.uniwuerzburg.omosim.core.Omosim
import de.uniwuerzburg.omosim.core.models.Cell
import de.uniwuerzburg.omosim.core.models.LocationOption
import org.locationtech.jts.geom.Coordinate
import java.io.*

class RouteChoiceCalibrationStore(
    val altPercentages: Map<ODTTriple, List<Double>>,
    val altMaxRoutes: Int,
    val altMaxSlower: Double,
    val altMaxSimilarity: Double,
) {
    companion object {
        fun getAreaCacheID(omosim: Omosim) : String {
            val bound = omosim.focusArea.envelopeInternal
            return "AreaBounds${listOf(bound.minX, bound.maxX, bound.minY, bound.maxY)
                .toString()
                .replace(" ", "")}" +
                    "Buffer${omosim.bufferRadius}"
        }

        fun write(
            omosim: Omosim,
            file: File,
            calibration: RouteChoiceCalibrationStore
        ) {
            val areaCacheID = getAreaCacheID(omosim)

            // Format for cache
            val oCoords = mutableListOf<Coordinate>()
            val dCoords = mutableListOf<Coordinate>()
            val ts = mutableListOf<Int>()
            val ps = mutableListOf<Array<Float>>()
            for ((odt, p) in calibration.altPercentages) {
                oCoords.add(odt.origin.latlonCoord)
                dCoords.add(odt.destination.latlonCoord)
                ts.add(odt.t)
                ps.add(p.map { it.toFloat() }.toTypedArray())
            }
            val formated = StorageFormat(
                areaCacheID,
                calibration.altMaxRoutes,
                calibration.altMaxSlower,
                calibration.altMaxSimilarity,
                oCoords.toTypedArray(),
                dCoords.toTypedArray(),
                ts.toTypedArray(),
                ps.toTypedArray()
            )

            // Save
            val fos = FileOutputStream(file)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(formated)
            fos.close()
            oos.close()
        }

        fun read(omosim: Omosim, file: File, grid: List<Cell>) : RouteChoiceCalibrationStore? {
            val areaCacheID = getAreaCacheID(omosim)

            val fis = FileInputStream(file)
            val ois = ObjectInputStream(fis)
            val data = ois.readObject() as StorageFormat
            fis.close()
            ois.close()

            // Check if calibration file fits the current simulation area.
            if (data.areaCacheID != areaCacheID) {
                logger.warn(
                    "Route choice calibration file does not match the currently simulated area. " +
                            "Ignoring calibration file."
                )
                return null
            }

            // Find locations for coords
            val allCoords = data.dCoords.toSet().union(data.oCoords.toSet())
            val coordLocMap = mutableMapOf<Coordinate, LocationOption>()
            for (coord in allCoords) {
                if (coordLocMap.containsKey(coord)) { continue }
                for (location in grid) {
                    if ((coord.x == location.latlonCoord.x) and (coord.y == location.latlonCoord.y)) {
                        coordLocMap[coord] = location
                        break
                    }
                }
            }

            // Unpack stored data
            val sizeData = data.oCoords.size
            var failures = 0
            val unpacked = mutableMapOf<ODTTriple, List<Double>>()
            for (i in 0 until sizeData) {
                val o = coordLocMap[data.oCoords[i]]
                val d = coordLocMap[data.dCoords[i]]
                val t = data.ts[i]
                val p = data.ps[i]

                // Check if cell exists in grid
                if ((o == null) or (d == null)) {
                    failures += 1
                    continue
                }

                val odt = ODTTriple(o!!, d!!, t)
                unpacked[odt] = p.map { it.toDouble() }.toList()
            }

            if (failures > 0) {
                logger.warn("Route choice calibration: couldn't match $failures calibrated od pairs to grid.")
            }

            return RouteChoiceCalibrationStore(
                unpacked,
                data.altMaxRoutes,
                data.altMaxSlower,
                data.altMaxSimilarity
            )
        }
    }

    /**
     * Storage format
     */
    private class StorageFormat  (
        val areaCacheID: String,
        val altMaxRoutes: Int,
        val altMaxSlower: Double,
        val altMaxSimilarity: Double,
        val oCoords: Array<Coordinate>,
        val dCoords: Array<Coordinate>,
        val ts: Array<Int>,
        val ps: Array<Array<Float>>
    ) : Serializable
}