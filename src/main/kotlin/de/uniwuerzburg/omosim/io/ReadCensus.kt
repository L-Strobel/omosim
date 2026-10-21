package de.uniwuerzburg.omosim.io

import de.uniwuerzburg.omosim.core.models.PopulationConfig
import de.uniwuerzburg.omosim.io.geojson.GeoJsonFeatureCollection
import de.uniwuerzburg.omosim.io.geojson.property.CensusProperties
import de.uniwuerzburg.omosim.io.json.readJsonStream
import de.uniwuerzburg.omosim.io.osm.BuildingData
import de.uniwuerzburg.omosim.utils.CRSTransformer
import org.apache.commons.math3.genetics.Population
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.index.hprtree.HPRtree
import java.nio.file.Path
import java.util.*
import kotlin.math.ceil
import kotlin.math.min

/**
 * Add census data to buildings
 *
 * @param osmBuildings Formated OSM buildings (Output of ReadOSM)
 * @param transformer Used for CRS conversions
 * @param geometryFactory Geometry factory
 * @param censusFile Distribution of the population in the area
 * @return List of buildings with all necessary features
 */
fun readCensus(
    osmBuildings: List<BuildingData>, transformer: CRSTransformer,
    geometryFactory: GeometryFactory, censusFile: Path, popConfig: PopulationConfig,
    rng: Random
) : List<BuildingData> {

    logger.info("Start reading census data...")
    // Spatial index
    val buildingsTree = HPRtree()
    for (building in osmBuildings) {
        buildingsTree.insert(building.geometry.envelopeInternal, building)
    }

    val censusData: GeoJsonFeatureCollection<CensusProperties> = readJsonStream(censusFile)

    for (censusEntree in censusData.features) {
        var population = censusEntree.properties.population
        if (population <= 0) { continue }

        // Save the local strata as an array and pass it to the building
        // TODO: change to map
        val zoneShares: DoubleArray = popConfig.strata.map {
            stratum -> censusEntree.properties.strata[stratum.stratumName]
            ?: error("Census entry has no value for stratum '${stratum.stratumName}'")
        }.toDoubleArray()

        val censusZone = transformer.toModelCRS(censusEntree.geometry.toJTS(geometryFactory) )

        val intersectingBuildings = buildingsTree.query(censusZone.envelopeInternal)
            .map { it as BuildingData }
            .filter { it.geometry.intersects(censusZone) }
            .shuffled(rng)

        val populationPerBuilding = ceil(population / intersectingBuildings.count().toDouble())
        for (building in intersectingBuildings) {
            val populationBuilding = min(population, populationPerBuilding)
            building.population = (building.population ?: 0.0) + populationBuilding
            building.localStrata = zoneShares
            population -= populationBuilding
            if (population <= 0) {
                break
            }
        }
    }
    if (osmBuildings.sumOf { it.population ?: 0.0 } <= 0) {
        logger.warn("Population in model area is zero!")
    }
    logger.info("Census data read!")
    return osmBuildings
}