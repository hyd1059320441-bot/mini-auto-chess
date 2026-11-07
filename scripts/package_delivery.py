#!/usr/bin/env python3
"""Package the local APK and source without host config or build caches."""
from pathlib import Path
import hashlib
import json
import shutil
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"


if __name__ == "__main__":
    DIST.mkdir(exist_ok=True)
    apk = DIST / "jade-chess-v1.4-preview.apk"
    shutil.copy2(ROOT / "app/build/outputs/apk/debug/app-debug.apk", apk)
    with zipfile.ZipFile(apk) as package:
        sky = package.read("assets/environment/jade_garden_sky.glb")
        sky_json_length = struct.unpack_from("<I", sky, 12)[0]
        sky_document = json.loads(sky[20:20 + sky_json_length])
        sky_factor = sky_document["materials"][0]["pbrMetallicRoughness"]["baseColorFactor"]
        assert sky_factor == [1., 1., 1., 1.], "Packaged sky must have full brightness and opacity"
        portrait_files = [name for name in package.namelist()
                          if Path(name).name.startswith("hero_") and name.endswith(".jpg")]
        assert len(portrait_files) == 12, "All twelve hero portraits must be packaged"
    archive = DIST / "jade-chess-v1.4-source.zip"
    roots = ["app/src", "gradle", "scripts", "docs", ".pico/accessibility"]
    files = [
        "README.md", "PRODUCT.md", "ART_DIRECTION.md", "PICO_AUTH_LOGIN_FINDING.md",
        "build.gradle.kts", "settings.gradle.kts", "gradlew", "gradlew.bat",
        "app/build.gradle.kts", "app/proguard-rules.pro",
    ]
    # Only include Gradle settings when they contain no credential-shaped keys.
    properties = ROOT / "gradle.properties"
    keys = [line.split("=", 1)[0].lower() for line in properties.read_text().splitlines()
            if "=" in line and not line.lstrip().startswith("#")]
    if not any(any(word in key for word in ("token", "password", "secret", "credential"))
               for key in keys):
        files.append("gradle.properties")
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
        for relative in files:
            path = ROOT / relative
            if path.is_file():
                output.write(path, path.relative_to(ROOT))
        for relative in roots:
            for path in sorted((ROOT / relative).rglob("*")):
                if path.is_file() and not any(part in {".DS_Store", "__pycache__", ".venv"}
                                             for part in path.parts):
                    output.write(path, path.relative_to(ROOT))
    manifest = {
        "apk": apk.name, "bytes": apk.stat().st_size,
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "source": archive.name,
        "package": "com.pico.miniautochess",
        "models": {"formal": 5, "prototype": 7, "references": 12},
        "version": "1.4", "version_code": 5, "board": [8, 8], "portraits": 12,
        "hud": "bottom_1280x512dp_multiline_notice_xp_gap", "native_fx_pool": 24, "particle_cap": 576,
        "status": "1280x600dp_traits_live_ranking_damage_income", "board_surface_y_meters": .265,
        "round_xp": 2, "round_base_income": 5,
        "interest": "floor(balance_after_immediate_win_coin/10)",
        "round_income_includes_newly_eliminated": True, "equipment": False,
        "player_damage": "round_base_damage + enemy_living_population; clamp_at_zero",
        "immediate_battle_results": True, "star_marker": "centered_textured_five_point_stars",
        "star_marker_y_meters": .36, "health_bar_y_meters": .405,
        "environment": "jade_garden_sky_stone_paving_deep_greenery_wood_courtyard",
        "charged_3d_tasks_this_revision": 0,
        "asset_checks": {"sky_base_color_rgba": sky_factor,
                         "packaged_portrait_count": len(portrait_files)},
    }
    (DIST / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))
