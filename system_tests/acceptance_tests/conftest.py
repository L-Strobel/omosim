import pytest
import utils
import run_sim
import tomllib
from pathlib import Path
import geopandas as gpd
import mid_17
import process_sim_ouput

with open("resources/config.toml", "rb") as f:
    file_locations = tomllib.load(f)

def download_ger_osm() -> str:
    osm_link = file_locations["weblinks"]["osmGermany"]

    fn_store = "resources/germany.osm.pbf"
    if not Path(fn_store).exists():
        utils.download_file(osm_link, fn_store)

    return fn_store

def download_ger_gtfs() -> str:
    gtfs_link = file_locations["weblinks"]["gtfsGermany"]

    fn_store = "resources/germany_gtfs.zip"
    if not Path(fn_store).exists():
        utils.download_file(gtfs_link, fn_store)

    return fn_store

class MIDAcceptanceData:

    def __init__(self, gdf_area, resolutions, mid_trips, mid_persons, omosim, omosim_trips):
        self.gdf_area = gdf_area
        self.resolutions = resolutions
        self.mid_trips = mid_trips
        self.mid_persons = mid_persons
        self.omosim = omosim
        self.omosim_trips = omosim_trips


@pytest.fixture(scope="session")
def nuremberg():
    area = "resources/nuremberg.geojson"
    osm = download_ger_osm()
    gtfs = download_ger_gtfs()
    run_sim.run_sim(area, osm, None, gtfs, "test_output/nuremberg.json")

    gdf_area = gpd.read_file(area)
    resolutions = utils.get_inspire_grids(gdf_area)
    mid_trips, mid_persons = mid_17.get_mid_17()
    omosim_all, omosim_trips = process_sim_ouput.get_omosim("test_output/nuremberg.json", resolutions)

    at_data = MIDAcceptanceData(
        gdf_area,
        resolutions,
        mid_trips,
        mid_persons,
        omosim_all,
        omosim_trips
    )

    return at_data




