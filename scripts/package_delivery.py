#!/usr/bin/env python3
"""Package the local APK and source without host config or build caches."""
from pathlib import Path
import hashlib
import json
import shutil
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "v1.10.0"
DIST = ROOT / "dist" / VERSION


if __name__ == "__main__":
    DIST.mkdir(parents=True, exist_ok=True)
    apk = DIST / f"jade-chess-{VERSION}-preview.apk"
    shutil.copy2(ROOT / "app/build/outputs/apk/debug/app-debug.apk", apk)
    with zipfile.ZipFile(apk) as package:
        sky = package.read("assets/environment/jade_garden_sky.glb")
        sky_json_length = struct.unpack_from("<I", sky, 12)[0]
        sky_document = json.loads(sky[20:20 + sky_json_length])
        sky_factor = sky_document["materials"][0]["pbrMetallicRoughness"]["baseColorFactor"]
        assert sky_factor == [1., 1., 1., 1.], "Packaged sky must have full brightness and opacity"
        portrait_files = [name for name in package.namelist()
                          if Path(name).name.startswith("hero_") and name.endswith(".jpg")]
        assert len(portrait_files) == 16, "All sixteen hero portraits must be packaged"
        assert len({hashlib.sha256(package.read(name)).hexdigest() for name in portrait_files}) == 16
        audio_manifest = json.loads(package.read("assets/audio/audio_manifest.json"))
        skill_audio = [name for name in package.namelist() if name.startswith("assets/audio/skill_") and name.endswith(".wav")]
        assert len(skill_audio) == 16
        assert len({hashlib.sha256(package.read(name)).hexdigest() for name in skill_audio}) == 16
        assert "assets/audio/garden_music.wav" in package.namelist()
        for filename, stats in audio_manifest["files"].items():
            assert stats["peak"] < .95
    archive = DIST / f"jade-chess-{VERSION}-source.zip"
    roots = ["app/src", "gradle", "scripts"]
    files = [
        "README.md", "PRODUCT.md", "ART_DIRECTION.md", "AGENTS.md", "PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md",
        "docs/UX_V1_6.md", "docs/UX_V1_5.md", "docs/verification/DELIVERY_V1_6.md", "docs/verification/SDK_6_1_9.md",
        "docs/verification/DELIVERY_V1_6_1.md",
        "docs/verification/v1.6.1-runtime.json",
        "docs/UX_V1_6_2.md", "docs/verification/DELIVERY_V1_6_2.md",
        "docs/verification/v1.6.2-runtime.json", "docs/verification/v1.6.2-tests.json",
        "docs/verification/v1.6.2-placement.json", "docs/verification/v1.6.2-build.log",
        "docs/verification/v1.6.2-install.log", "docs/verification/v1.6.2-launch.log",
        "docs/verification/v1.6.2-app-info.json", "docs/verification/v1.6.2-startup.log",
        "docs/verification/v1.6.2-errors.log", "docs/verification/v1.6.2-installed-apk-check.json",
        "docs/verification/v1.6.2-android-runtime.log",
        "docs/UX_V1_7.md", "docs/verification/DELIVERY_V1_7.md",
        "docs/verification/v1.7-runtime.json", "docs/verification/v1.7-tests.json",
        "docs/verification/v1.7-placement.json", "docs/verification/v1.7-build.log",
        "docs/verification/v1.7-install.log", "docs/verification/v1.7-launch.log",
        "docs/verification/v1.7-app-info.json", "docs/verification/v1.7-startup.log",
        "docs/verification/v1.7-errors.log", "docs/verification/v1.7-installed-apk-check.json",
        "docs/verification/v1.7-android-runtime.log",
        "docs/verification/v1.7-crash.log",
        "docs/verification/simulation-v1.7-100.md", "docs/verification/simulation-v1.7-100.csv",
        "docs/models/STATUS.md", "docs/environment/jade_garden_sky.jpg", "docs/environment/assets.json",
        "docs/verification/simulation-100.md", "docs/verification/simulation-100.csv",
        "docs/verification/v1.6-tests.json", "docs/verification/v1.6-runtime.json",
        "docs/verification/v1.6-build.log", "docs/verification/v1.6-install.log",
        "docs/verification/v1.6-launch.log", "docs/verification/v1.6-app-info.json",
        "docs/verification/v1.6-startup.log", "docs/verification/v1.6-errors.log",
        "docs/verification/v1.6-audio-runtime.log",
        "docs/verification/v1.6-android-runtime.log", "docs/verification/v1.6-placement.json",
        "docs/verification/v1.6-installed-apk-check.json",
        "build.gradle.kts", "settings.gradle.kts", "gradlew", "gradlew.bat",
        "app/build.gradle.kts", "app/proguard-rules.pro",
    ]
    files.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "docs/models").glob("*.jpg")))
    files.extend([
        "docs/UX_V1_7_1.md", "docs/verification/DELIVERY_V1_7_1.md",
        "docs/verification/simulation-v1.7.1-100.md", "docs/verification/simulation-v1.7.1-100.csv",
    ])
    files.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "docs/verification").glob("v1.7.1-*")))
    files.extend([
        "docs/UX_V1_8.md", "docs/hero-data-v1.8.0.md", "docs/verification/DELIVERY_V1_8.md",
        "docs/verification/simulation-v1.8.0-100.csv", "docs/verification/simulation-v1.8.0-100.md",
        "docs/verification/strategy-v1.8.0-150.csv", "docs/verification/strategy-v1.8.0-150.md",
    ])
    files.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "docs/verification").glob("v1.8.0-*")))
    files.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "docs/balance-v1.8.0").rglob("*")) if path.is_file())
    files.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "docs/balance-v1.7.1").rglob("*")) if path.is_file())
    files.append("docs/hero-data-v1.7.1.md")
    files.extend([
        "docs/UX_V1_9.md", "docs/verification/DELIVERY_V1_9.md",
        "docs/verification/simulation-v1.9.0-100.csv", "docs/verification/simulation-v1.9.0-100.md",
        "docs/verification/strategy-v1.9.0-150.csv", "docs/verification/strategy-v1.9.0-150.md",
    ])
    files.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "docs/verification").glob("v1.9.0-*")))
    files.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "docs/fairness-v1.9.0").rglob("*")) if path.is_file())
    files.extend([
        "docs/UX_V1_10.md", "docs/verification/DELIVERY_V1_10.md",
        "docs/verification/simulation-v1.10.0-100.csv", "docs/verification/simulation-v1.10.0-100.md",
        "docs/verification/strategy-v1.10.0-150.csv", "docs/verification/strategy-v1.10.0-150.md",
    ])
    files.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "docs/verification").glob("v1.10.0-*")))
    files.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "docs/fairness-v1.10.0").rglob("*")) if path.is_file())
    tests = json.loads((ROOT / f"docs/verification/{VERSION}-tests.json").read_text())
    assert tests["passed"] and tests["failures"] == 0 and tests["errors"] == 0
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
                output.write(path, Path("mini-auto-chess") / path.relative_to(ROOT))
        for relative in roots:
            for path in sorted((ROOT / relative).rglob("*")):
                if path.is_file() and not any(part in {".DS_Store", "__pycache__", ".venv"}
                                             for part in path.parts):
                    output.write(path, Path("mini-auto-chess") / path.relative_to(ROOT))
    manifest = {
        "apk": apk.name, "bytes": apk.stat().st_size,
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "source": archive.name,
        "source_bytes": archive.stat().st_size,
        "source_sha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
        "package": "com.pico.miniautochess",
        "models": {"formal": 5, "articulated": 11, "reference_portraits": 16, "designed_emblems": 0},
        "version": "V1.10.0", "version_code": 15, "board": [8, 8], "heroes": 16,
        "scene_forward_offset_z_meters": -.30,
        "hero_visual_scale": .85, "status_label_scale": 1.0,
        "hud": "bottom_1280x600dp_streak_pool_odds_xp_gap", "native_fx_pool": 40, "spark_mesh_cap": 240,
        "projectile_pool": 32,
        "visuals": {"skill_wall_seconds": [1.2, 1.8], "full_brightness_fraction": .6,
                    "projectile_wall_seconds": [.3, .55], "arrows_and_orbs": True,
                    "captured_event_endpoints": True, "damage_timing_unchanged": True,
                    "pause": "freeze", "spectate_round_restart": "clear_old_effects",
                    "skill_priority": True, "real_time_lights": 0},
        "status": "1280x600dp_traits_live_ranking_damage_income", "board_surface_y_meters": .265,
        "round_xp": 2, "round_base_income": 5,
        "interest": "floor(balance_after_immediate_win_coin/10)",
        "streak_bonus": {"2_3": 1, "4": 2, "5_plus": 3, "win_loss_same": True,
                         "draw_resets": True, "paid_after_interest_snapshot": True, "ghost_donor_unchanged": True},
        "round_income_includes_newly_eliminated": True, "equipment": False,
        "player_damage": "round_base_damage + enemy_living_population; clamp_at_zero",
        "late_round_base_damage": "round11 starts at6; increases1 every2 rounds",
        "round_cap": None, "finish_condition": "human_eliminated OR living_players<=1",
        "early_finish": "pay_completed_real_results_once; freeze_unfinished_battles; living_AI_marked_unfinished",
        "text_line_height": "explicit_for_all_enlarged_fonts; odds_26sp/33sp",
        "combat": {"normal_seconds": 25, "overtime_seconds": 15, "overtime_speed": 2,
                   "overtime_recovery_multiplier": .5, "nova_secondary_multiplier": .65,
                   "fallback": "count_then_health_ratio_then_draw; only_after_overtime"},
        "balance": {"base_magic_resist": 20, "guard_armor_magic_resist": [8, 20, 36],
                    "support_team_magic_resist": [10, 25], "mage_spell_bonus": [.1, .2, .35],
                    "mage_initial_mana": [20, 30, 40], "archer_attack_speed": [.1, .25, .4],
                    "hp_attack_stars": [1, 1.8, 3.24], "skill_curves": "per_hero",
                    "army_selection": "bounded_group_search_actual_combat_stats",
                    "inherited_from": "V1.8.0", "rerun_this_revision": False,
                    "evidence": json.loads((ROOT / "docs/balance-v1.8.0/evidence.json").read_text())},
        "battle_economy": {"buy_refresh_xp": True, "bench_move_swap_sell": True,
                          "board_locked_through_transition": True,
                          "merge": "bench_only_until_next_preparation",
                          "new_population": "next_preparation"},
        "terminal_dialog": {"native": True, "titles": ["胜利", "失败"],
                            "buttons": ["再来一局", "退出"], "exit": "LocalSpatialNavigator.closeStage"},
        "fairness": {"new_seed_per_match": True, "rotating_initial_shop_order": True,
                     "ai_first_action_seconds": [2, 4], "ai_next_action_seconds": [.8, 2],
                     "ai_steps": ["buy", "xp", "refresh_buy", "refresh_buy"],
                     "ai_styles_shuffled_each_match": True,
                     "same_style_seat_audit": json.loads((ROOT / "docs/fairness-v1.10.0/summary.json").read_text())},
        "drag": {"grab_box_meters": [.21, .34, .21], "grab_center_y": .15,
                 "occupied_slot_can_start_drag": True, "bench_collider": "thin_tile",
                 "sale": "release_in_measured_shop_central_area; exact_once_rule_sell"},
        "quality_colors_by_fee": {"1": "#F5F5F5", "2": "#55C76B", "3": "#4B91FF"},
        "immediate_battle_results": True, "star_marker": "centered_textured_five_point_stars",
        "status_markers": "animated_body_bounds_centerXZ_and_maxY; hp100_and_mana5_ticks; current/max_cached_numeric_glyphs",
        "mana_cap": 100, "full_mana_cast_independent_of_basic_attack": True,
        "cost_pool_sizes": {"1": 26, "2": 20, "3": 14},
        "odds": "population_level_only; TFT 4/5-cost weights merged into 3-cost; empty supported fees renormalize",
        "odds_by_level": {"1": [100, 0, 0], "2": [100, 0, 0], "3": [75, 25, 0],
                          "4": [55, 30, 15], "5": [45, 33, 22], "6": [30, 40, 30], "7": [16, 30, 54]},
        "traits": {"factions": [2, 4], "guard": [1, 3, 5], "mage": [1, 3, 5],
                   "archer": [1, 2, 3], "support": [1, 3]},
        "ghost": "random other live seat, current locked army, negative UIDs, no donor effects",
        "selection": "drag_uid_keeps_gold_material; pulsing_square_outline_and_dragging_label; restore_on_release_or_cancel",
        "sdk": "6.1.9",
        "environment": "continuous_jade_tabletop_front_pavilion_beam_roof_lotus_and_garden_seats",
        "visible_grid": False,
        "hero_actions": ["bench", "ready", "walk", "attack", "cast", "victory"],
        "animation_assets": {"rigged_glb": 0, "whole_body_procedural_glb": 5, "articulated_procedural": 11},
        "skill_motifs": 16, "skill_sounds": 16, "shared_attack_sounds": 3,
        "audio_backend": "SoundPool_8_voices_and_MediaPlayer_loop",
        "music": {"original": True, "bpm": 108, "bars": 32, "toggle": True,
                  "loop_seam_pcm_delta": audio_manifest["loop_seam"]},
        "charged_3d_tasks_this_revision": 0,
        "tests_passed": tests["tests"], "simulation_matches": 100, "strategy_simulation_matches": 150,
        "asset_checks": {"sky_base_color_rgba": sky_factor,
                         "packaged_portrait_count": len(portrait_files)},
    }
    (DIST / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    shutil.copy2(ROOT / "docs/verification/DELIVERY_V1_6.md", DIST / "DELIVERY_V1_6.md")
    shutil.copy2(ROOT / "docs/verification/DELIVERY_V1_6_1.md", DIST / "DELIVERY_V1_6_1.md")
    shutil.copy2(ROOT / "docs/verification/DELIVERY_V1_6_2.md", DIST / "DELIVERY_V1_6_2.md")
    shutil.copy2(ROOT / "docs/verification/DELIVERY_V1_7.md", DIST / "DELIVERY_V1_7.md")
    shutil.copy2(ROOT / "docs/verification/DELIVERY_V1_7_1.md", DIST / "DELIVERY_V1_7_1.md")
    shutil.copy2(ROOT / "docs/verification/DELIVERY_V1_8.md", DIST / "DELIVERY_V1_8.md")
    shutil.copy2(ROOT / "docs/verification/DELIVERY_V1_9.md", DIST / "DELIVERY_V1_9.md")
    shutil.copy2(ROOT / "docs/verification/DELIVERY_V1_10.md", DIST / "DELIVERY_V1_10.md")
    print(json.dumps(manifest, indent=2))
