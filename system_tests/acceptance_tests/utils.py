import pytest

import geopandas as gpd
import requests
from pathlib import Path
import tomllib
import zipfile
from tqdm import tqdm

with open("resources/config.toml", "rb") as f:
    file_locations = tomllib.load(f)

def download_file(url, output_filename):
    chunk_size = 8192
    with requests.get(url, stream=True) as response:
        response.raise_for_status()
        total_size = int(response.headers.get('content-length', 0))
        progress_bar = tqdm(total=total_size, unit='iB', unit_scale=True, desc="Downloading")

        with open(output_filename, "wb") as file:
            for chunk in response.iter_content(chunk_size=chunk_size):
                if chunk:
                    progress_bar.update(len(chunk))
                    file.write(chunk)

        progress_bar.close()

def unzip_inspire(file, output_filename):
    with zipfile.ZipFile(file, "r") as zip_ref:
        # Find grid
        matched_file = None
        for file_path in zip_ref.namelist():
            if file_path.endswith(".gpkg"):
                matched_file = file_path
                break
        # Extract
        with zip_ref.open(matched_file) as source_file:
            with open(output_filename, "wb") as dest_file:
                dest_file.write(source_file.read())

    Path(file).unlink() # Delete zip file

def get_inspire_grids(area):
    use_cols = ['OBJECTID', 'id', 'geometry']

    fn500 = "resources/inspire500.gpkg"
    if not Path(fn500).exists():
        download_file(file_locations["weblinks"]["inspire500"], fn500 + ".zip")
        unzip_inspire(fn500 + ".zip", fn500)
    inspire500 = gpd.read_file(fn500, mask=area).to_crs(epsg=3857)
    inspire500 = inspire500[use_cols].set_index("id")

    fn1k = "resources/inspire1k.gpkg"
    if not Path(fn1k).exists():
        download_file(file_locations["weblinks"]["inspire1k"], fn1k + ".zip")
        unzip_inspire(fn1k + ".zip", fn1k)
    inspire1k = gpd.read_file(fn1k, mask=area).to_crs(epsg=3857)
    inspire1k = inspire1k[use_cols].set_index("id")

    fn5k = "resources/inspire5k.gpkg"
    if not Path(fn5k).exists():
        download_file(file_locations["weblinks"]["inspire5k"], fn5k + ".zip")
        unzip_inspire(fn5k + ".zip", fn5k)
    inspire5k = gpd.read_file(fn5k, mask=area).to_crs(epsg=3857)
    inspire5k = inspire5k[use_cols].set_index("id")

    return {"500m": inspire500, "1km": inspire1k, "5km": inspire5k}
