import setup
import geopandas as gpd

def test_dummy():
    area = gpd.read_file("resources/nbg.geojson")
    setup.get_inspire_grids(area)
    assert 5 == 5
