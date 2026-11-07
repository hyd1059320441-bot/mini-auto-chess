"""Create app assets from SGS sources; preserve originals and record all changes."""
from pathlib import Path
import json
import numpy as np
import trimesh
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "app/src/main/assets/models"
DEST.mkdir(parents=True, exist_ok=True)
records = []
# Measured in original source coordinates: circular plinth ends at this plane.
# UVs are interpolated by slice_mesh_plane; feet above the support plane remain.
CUT_Y = {"copper_guard": -.32, "forest_ranger": -.374, "rune_apprentice": -.407,
         "ember_acolyte": -.387, "moss_guard": -.355}
for path in sorted((ROOT / "docs/models").glob("*.glb")):
    scene = trimesh.load(path, force="scene", process=False)
    edits = []
    for name, mesh in list(scene.geometry.items()):
        before = len(mesh.faces)
        cut = trimesh.intersections.slice_mesh_plane(
            mesh, plane_normal=[0, 1, 0], plane_origin=[0, CUT_Y[path.stem], 0], cap=False)
        assert len(cut.faces) > before * .70
        assert len(cut.visual.uv) == len(cut.vertices)
        assert np.isfinite(cut.visual.uv).all()
        scene.geometry[name] = mesh = cut
        edits.append({"removed_circular_plinth_below_y": CUT_Y[path.stem],
                      "source_faces": before, "retained_faces": len(cut.faces),
                      "uv_interpolated": True})
        mat = mesh.visual.material
        image = getattr(mat, "baseColorTexture", None)
        if image is not None:
            pix = np.asarray(image.convert("RGB")).astype(float) / 255
            chroma = pix.max(axis=2) - pix.min(axis=2)
            if np.percentile(chroma, 90) < .06:
                # The copper sample is achromatic. Preserve baked light/shadow while
                # replacing neutral gray with ivory and dark regions with jade.
                light = pix.mean(axis=2)
                copper = np.array([184, 126, 66]) / 255
                steel = np.array([67, 101, 116]) / 255
                palette = np.where((light < .40)[:, :, None], steel, copper)
                pix = palette * (.4 + .6 * light[:, :, None])
                edits.append("achromatic_texture_mapped_to_copper_steel_preserving_shading")
            mat.baseColorTexture = Image.fromarray((pix * 255).astype("uint8")).resize((1024, 1024), Image.Resampling.LANCZOS)
            edits.append("base_color_texture_1024")
        mat.metallicFactor = 0.
        mat.roughnessFactor = .92
        edits.append("matte_nonmetallic")
    data = scene.export(file_type="glb")
    (DEST / path.name).write_bytes(data)
    records.append({"hero": path.stem, "source": str(path.relative_to(ROOT)),
                    "asset": str((DEST / path.name).relative_to(ROOT)), "changes": edits})
(ROOT / "docs/models/preparation.json").write_text(json.dumps(records, ensure_ascii=False, indent=2))
print(json.dumps(records, ensure_ascii=False, indent=2))
