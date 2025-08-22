package de.uniwuerzburg.omosim.io.gtfs

interface GTFSFilter {
    fun filter(record: List<String>, idxMap: Map<String, Int>) : Boolean
}