import utils
import geopandas as gpd

def test_dummy(nuremberg):
    assert nuremberg.returncode == 0, "Sim failed"
