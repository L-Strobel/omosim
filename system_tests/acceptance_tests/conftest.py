import pytest
import utils
import run_sim
import tomllib
from pathlib import Path

with open("resources/file_locations.toml", "rb") as f:
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

@pytest.fixture(scope="session")
def nuremberg():
    area = "resources/nuremberg.geojson"
    osm = download_ger_osm()
    gtfs = download_ger_gtfs()

    return run_sim.run_sim(area, osm, None, gtfs, "test_output/nuremberg.json")


