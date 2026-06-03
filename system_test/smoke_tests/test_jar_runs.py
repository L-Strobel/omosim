import subprocess
import os
import re

def test_jar_runs():
    jar_path = os.getenv("APP_JAR_PATH")

    if not jar_path or not os.path.exists(jar_path):
        raise AssertionError(f"JAR file not found at path: {jar_path}")

    result = subprocess.run(
        [
            "java", "-jar", jar_path,
            "../src/test/resources/test_area.geojson", "../src/test/resources/test.osm.pbf",
            "--n_agents", "10", "--out", "test_output/smoke_runs.json"
         ],
        capture_output=True,
        text=True,
        timeout=10
    )

    assert result.returncode == 0, f"JAR failed! Error log:\n{result.stderr}"
    assert re.search(r"Saving results to .* Done!", result.stdout), f"Run did not complete successfully!"