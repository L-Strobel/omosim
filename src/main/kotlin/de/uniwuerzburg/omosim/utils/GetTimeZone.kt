package de.uniwuerzburg.omosim.utils

import org.locationtech.jts.geom.Geometry
import us.dustinj.timezonemap.TimeZoneMap
import java.util.TimeZone

/**
 * Determine time zone at the center of the area.
 *
 * @param area Must be in lat/lon coordinates.
 */
fun getTimeZone(area: Geometry) : TimeZone {
    val map = TimeZoneMap.forRegion(
        area.envelopeInternal.minX, area.envelopeInternal.minY,
        area.envelopeInternal.maxX, area.envelopeInternal.maxY
    )
    val tzString = map.getOverlappingTimeZone(area.centroid.x, area.centroid.y)?.zoneId
    return TimeZone.getTimeZone(tzString)
}