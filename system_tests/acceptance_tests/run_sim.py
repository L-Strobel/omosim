import subprocess
import os
import re

def run_sim(fn_area, fn_osm, fn_census, fn_gtfs, fn_out):
    jar_path = os.getenv("APP_JAR_PATH")

    if not jar_path or not os.path.exists(jar_path):
        raise AssertionError(f"JAR file not found at path: {jar_path}")

    result = subprocess.run(
        [
            "java", "-jar", jar_path,
            fn_area,
            fn_osm,
            # "--census", fn_census, # TODO
            "--gtfs_file", fn_gtfs,
            "--buffer", "0", # TODO 40000
            "--n_agents", "1", # TODO 10000
            "--routing_mode", "GRAPHHOPPER",
            "--start_wd", "undefined",
            "--n_days", "4",
            "--populate_buffer_area", "y",
            "--mode_choice", "FAST",
            "--out", f"test_output/{fn_out}",
            "--cache_dir", "test_output/omosim_cache",
        ],
        capture_output=True,
        text=True,
    )

    return result