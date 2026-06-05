import json
import pandas as pd
import geopandas as gpd

def get_omosim(fn, resolutions):
    # Read file
    with open(fn, "r", encoding="utf-8") as  f:
        data = json.load(f)
    # Parse to table
    records = []
    for agent in data["agents"]:
        agentInfos = {}
        for ka, va in agent.items():
            if ka == "mobilityDemand": continue
            agentInfos[ka] = va

        for diary in agent["mobilityDemand"]:
            diaryInfos = {}
            for kd, vd in diary.items():
                if kd == "plan": continue
                diaryInfos[kd] = vd

            for leg in diary["plan"]:
                record = agentInfos | diaryInfos | leg
                records.append(record)
    omosim = pd.DataFrame(records)

    # Extract trips
    omosimTrips = omosim[omosim["type"] == "Trip"].copy()

    # Find activity before and after
    starts = omosim.loc[omosimTrips.index - 1]
    stops = omosim.loc[omosimTrips.index + 1]
    assert((starts["type"] == "Activity").all())
    assert((stops["type"] == "Activity").all())

    omosimTrips["startAct"] = starts.activityType.values
    omosimTrips["stopAct"] = stops.activityType.values

    # Add start stop geometries and inspire grid cells
    omosimTrips["startLoc"] = gpd.points_from_xy(starts.lon, starts.lat, crs=4326)
    omosimTrips["stopLoc"] = gpd.points_from_xy(stops.lon, stops.lat, crs=4326)
    omosimTrips = gpd.GeoDataFrame(omosimTrips, geometry="startLoc")
    for resolution, inspire in resolutions.items():
        omosimTrips = omosimTrips.set_geometry("startLoc").to_crs(epsg=3857)
        omosimTrips["GITTER_SO_" + resolution] = gpd.sjoin(omosimTrips, inspire, predicate='within').id_right
        omosimTrips = omosimTrips.set_geometry("stopLoc").to_crs(epsg=3857)
        omosimTrips["GITTER_ZO_" + resolution] = gpd.sjoin(omosimTrips, inspire, predicate='within').id_right
        omosimTrips = omosimTrips.set_geometry("startLoc")

    omosim["actLocation"] = gpd.points_from_xy(omosim.lon, omosim.lat, crs=4326)
    omosim = gpd.GeoDataFrame(omosim, geometry="actLocation").to_crs(epsg=3857)
    for resolution, inspire in resolutions.items():
        omosim["GITTER_A_" + resolution] = gpd.sjoin(omosim, inspire, predicate='within').id_right

    # For MID compatability
    omosimTrips["W_GEW"] = 1
    return omosim, omosimTrips