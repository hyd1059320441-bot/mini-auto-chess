"""Inspect actual GLBs and render orthographic material previews without an Editor."""
from pathlib import Path
import hashlib
import json
import numpy as np
import trimesh
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/models"
report = []
for path in sorted(OUT.glob("*.glb")):
    scene = trimesh.load(path, force="scene", process=False)
    meshes = []
    materials = []
    for node in scene.graph.nodes_geometry:
        transform, key = scene.graph[node]
        mesh = scene.geometry[key].copy()
        mesh.apply_transform(transform)
        meshes.append(mesh)
        mat = getattr(mesh.visual, "material", None)
        img = getattr(mat, "baseColorTexture", None)
        materials.append({
            "name": getattr(mat, "name", None),
            "texture_size": list(img.size) if img is not None else None,
            "metallic": getattr(mat, "metallicFactor", None),
            "roughness": getattr(mat, "roughnessFactor", None),
        })
    bounds = scene.bounds
    record = {
        "hero": path.stem,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "triangles": sum(len(m.faces) for m in meshes),
        "bounds_meters": bounds.tolist(),
        "extents_meters": (bounds[1] - bounds[0]).tolist(),
        "materials": materials,
        "preview": f"{path.stem}_views.png",
        "preview_method": "Orthographic triangle projection, UV centroid color, diffuse shading; not SDK runtime proof",
    }
    fig, axes = plt.subplots(1, 3, figsize=(12, 5), facecolor="#eceae4")
    for ax, (label, horizontal, depth, sign) in zip(
        axes, [("+Z view", 0, 2, 1), ("+X view", 2, 0, 1), ("-Z view", 0, 2, -1)]
    ):
        triangles, colors, distances = [], [], []
        for mesh in meshes:
            verts = mesh.triangles
            uv = getattr(mesh.visual, "uv", None)
            mat = getattr(mesh.visual, "material", None)
            img = getattr(mat, "baseColorTexture", None)
            if img is not None and uv is not None:
                pix = np.asarray(img.convert("RGB"))
                coords = uv[mesh.faces].mean(axis=1)
                xx = np.clip((coords[:, 0] * (pix.shape[1] - 1)).astype(int), 0, pix.shape[1] - 1)
                yy = np.clip(((1 - coords[:, 1]) * (pix.shape[0] - 1)).astype(int), 0, pix.shape[0] - 1)
                color = pix[yy, xx] / 255.
            else:
                color = np.tile([.75, .72, .65], (len(verts), 1))
            normal = mesh.face_normals
            shade = .65 + .35 * np.maximum(0, normal[:, depth] * sign)
            triangles.extend(verts[:, :, [horizontal, 1]] * [sign if horizontal == 0 else 1, 1])
            colors.extend(np.clip(color * shade[:, None], 0, 1))
            distances.extend(verts[:, :, depth].mean(axis=1) * sign)
        order = np.argsort(distances)
        ax.add_collection(PolyCollection(np.asarray(triangles)[order], facecolors=np.asarray(colors)[order], edgecolors="none"))
        ax.set_xlim(-.6, .6); ax.set_ylim(-.55, .6); ax.set_aspect("equal")
        ax.set_title(label); ax.axis("off"); ax.set_facecolor("#eceae4")
    fig.suptitle(path.stem)
    fig.tight_layout()
    fig.savefig(OUT / record["preview"], dpi=160)
    plt.close(fig)
    report.append(record)
(OUT / "inspection.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
print(json.dumps([{k: r[k] for k in ("hero", "triangles", "extents_meters")} for r in report], indent=2))
