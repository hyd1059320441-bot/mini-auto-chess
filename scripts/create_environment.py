"""Build inward equirectangular sky geometry and portraits from actual art."""
from pathlib import Path
import json
import struct
import numpy as np
import trimesh
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
assets = ROOT / "app/src/main/assets/environment"
assets.mkdir(parents=True, exist_ok=True)
drawable = ROOT / "app/src/main/res/drawable"
vertices, uv, faces = [], [], []
cols, rows, radius = 64, 32, 18.
for row in range(rows + 1):
    theta = np.pi * row / rows
    for col in range(cols + 1):
        phi = 2 * np.pi * col / cols
        vertices.append([radius * np.sin(theta) * np.sin(phi),
                         radius * np.cos(theta), radius * np.sin(theta) * np.cos(phi)])
        uv.append([col / cols, 1 - row / rows])
for row in range(rows):
    for col in range(cols):
        a = row * (cols + 1) + col
        faces.extend([[a, a + 1, a + cols + 1], [a + 1, a + cols + 2, a + cols + 1]])
mesh = trimesh.Trimesh(vertices=vertices, faces=faces, process=False)
# GLB front-face winding must point toward the player.
valid = mesh.area_faces > 1e-7
if np.mean(np.einsum("ij,ij->i", mesh.face_normals[valid], mesh.triangles_center[valid])) > 0:
    mesh.faces = mesh.faces[:, ::-1]
image = Image.open(ROOT / "docs/environment/jade_garden_sky.jpg").convert("RGB")
assert image.width == image.height * 2
mesh.visual = trimesh.visual.texture.TextureVisuals(
    uv=np.asarray(uv),
    material=trimesh.visual.material.PBRMaterial(
        baseColorTexture=image.resize((2048, 1024), Image.Resampling.LANCZOS),
        baseColorFactor=[255, 255, 255, 255], metallicFactor=0, roughnessFactor=1, doubleSided=False))
data = mesh.export(file_type="glb")
# Inspect the shipped material, since integer [1,1,1,1] is interpreted as 8-bit
# RGBA by trimesh and silently exports 1/255 (dark and almost transparent).
json_length = struct.unpack_from("<I", data, 12)[0]
document = json.loads(data[20:20 + json_length])
factor = document["materials"][0]["pbrMetallicRoughness"]["baseColorFactor"]
assert factor == [1., 1., 1., 1.], f"Sky texture tint/alpha must be fully white: {factor}"
assert np.all(np.einsum("ij,ij->i", mesh.face_normals[valid], mesh.triangles_center[valid]) < 0)
(assets / "jade_garden_sky.glb").write_bytes(data)
portraits = []
for source in sorted((ROOT / "docs/models").glob("*.jpg")):
    target = drawable / f"hero_{source.stem}.jpg"
    ImageOps.fit(Image.open(source).convert("RGB"), (320, 320),
                 method=Image.Resampling.LANCZOS).save(target, quality=91)
    portraits.append({"hero": source.stem, "source": str(source.relative_to(ROOT)),
                      "drawable": target.name, "dimensions": [320, 320]})
assert len(portraits) == 12
report = {"sky": {"asset": "environment/jade_garden_sky.glb", "radius_m": radius,
                  "texture": [2048, 1024], "triangles": len(faces), "inward": True,
                  "exported_base_color_rgba": factor, "material_verified": True},
          "portraits": portraits}
(ROOT / "docs/environment/assets.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
print(json.dumps(report, ensure_ascii=False, indent=2))
