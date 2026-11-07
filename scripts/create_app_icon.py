"""Render the application's jade knight icon; publishing conversion is separate."""

from pathlib import Path
import math

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
SIZE = 1024
SCALE = 3


def points(values):
    return [(round(x * SCALE), round(y * SCALE)) for x, y in values]


def curve(start, controls):
    result = [start]
    for a, b, end in controls:
        origin = result[-1]
        for step in range(1, 25):
            t = step / 24
            u = 1 - t
            result.append(
                (
                    u**3 * origin[0] + 3 * u * u * t * a[0]
                    + 3 * u * t * t * b[0] + t**3 * end[0],
                    u**3 * origin[1] + 3 * u * u * t * a[1]
                    + 3 * u * t * t * b[1] + t**3 * end[1],
                )
            )
    return result


def gradient(top, bottom):
    image = Image.new("RGB", (SIZE * SCALE, SIZE * SCALE))
    draw = ImageDraw.Draw(image)
    for y in range(SIZE * SCALE):
        t = y / (SIZE * SCALE - 1)
        color = tuple(round(a * (1 - t) + b * t) for a, b in zip(top, bottom))
        draw.line((0, y, SIZE * SCALE, y), fill=color)
    return image


def render():
    image = gradient((20, 48, 47), (7, 22, 27)).convert("RGBA")
    glow = Image.new("RGBA", image.size)
    draw = ImageDraw.Draw(glow)
    draw.ellipse(points([(285, 255), (785, 805)]), fill=(38, 143, 111, 65))
    image.alpha_composite(glow.filter(ImageFilter.GaussianBlur(115 * SCALE)))

    shadow = Image.new("RGBA", image.size)
    draw = ImageDraw.Draw(shadow)
    draw.ellipse(points([(270, 723), (770, 818)]), fill=(0, 0, 0, 155))
    image.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(17 * SCALE)))
    draw = ImageDraw.Draw(image)

    # A compact inlaid board, kept completely inside the launcher's safe circle.
    left, right, front, back = (269, 705), (755, 705), (512, 815), (512, 595)
    draw.polygon(points([left, back, right, front]), fill="#c6a763")
    draw.polygon(points([(269, 705), (512, 815), (755, 705),
                         (755, 723), (512, 833), (269, 723)]), fill="#705b38")
    for row in range(8):
        for col in range(8):
            def tile(x, y):
                return (512 + (x - y) * 28, 611 + (x + y) * 12.5)
            corners = [tile(col, row), tile(col + 1, row),
                       tile(col + 1, row + 1), tile(col, row + 1)]
            draw.polygon(points(corners), fill="#284f49" if (row + col) % 2 else "#427367")

    silhouette = curve((372, 701), [
        ((372, 646), (404, 619), (435, 565)),
        ((445, 543), (451, 522), (448, 507)),
        ((427, 514), (410, 502), (390, 498)),
        ((373, 496), (348, 483), (340, 467)),
        ((335, 455), (346, 440), (360, 428)),
        ((386, 410), (417, 392), (434, 377)),
        ((449, 361), (455, 333), (473, 321)),
        ((492, 309), (519, 296), (542, 284)),
        ((549, 260), (554, 246), (566, 226)),
        ((578, 246), (586, 264), (589, 284)),
        ((618, 300), (646, 319), (663, 346)),
        ((706, 409), (725, 487), (687, 561)),
        ((665, 605), (642, 636), (655, 701)),
        ((571, 721), (458, 721), (372, 701)),
    ])
    mask = Image.new("L", image.size)
    ImageDraw.Draw(mask).polygon(points(silhouette), fill=255)
    jade = gradient((139, 229, 186), (29, 117, 101)).convert("RGBA")
    image.paste(jade, (0, 0), mask)
    draw = ImageDraw.Draw(image)
    draw.line(points(silhouette + [silhouette[0]]), fill="#a0d5b1",
              width=3 * SCALE, joint="curve")

    # Sculpted mane, muzzle, eye and neck facets.
    draw.polygon(points([(589, 288), (644, 347), (670, 410), (678, 491),
                         (644, 566), (610, 626), (619, 696), (655, 701),
                         (654, 637), (687, 561), (709, 476), (688, 391),
                         (652, 330)]), fill="#246d61")
    draw.line(points(curve((600, 322), [
        ((674, 390), (682, 474), (632, 555)),
        ((607, 599), (583, 639), (599, 687)),
    ])), fill="#d2b875", width=7 * SCALE, joint="curve")
    draw.polygon(points([(475, 390), (527, 367), (551, 390), (505, 421)]),
                 fill="#2b6e5c")
    draw.ellipse(points([(482, 388), (499, 403)]), fill="#ffda83")
    draw.ellipse(points([(485, 390), (491, 396)]), fill="#fff1c8")
    draw.line(points([(354, 465), (389, 476), (423, 476)]),
              fill="#2c7963", width=5 * SCALE)
    draw.line(points(curve((482, 451), [
        ((505, 471), (530, 477), (558, 472)),
        ((564, 526), (526, 572), (486, 631)),
    ])), fill="#9cddb6", width=5 * SCALE, joint="curve")
    draw.line(points([(445, 338), (438, 372), (373, 428)]),
              fill="#cee9c6", width=3 * SCALE)

    draw.rounded_rectangle(points([(369, 691), (659, 724)]),
                           radius=12 * SCALE, fill="#cfb574")
    draw.rounded_rectangle(points([(365, 720), (663, 750)]),
                           radius=11 * SCALE, fill="#307a67")
    draw.line(points([(379, 725), (649, 725)]), fill="#79c6a2", width=3 * SCALE)
    draw.rounded_rectangle(points([(351, 746), (677, 773)]),
                           radius=10 * SCALE, fill="#bc9e60")

    # Three restrained stars reference the game's upgrade mechanic.
    for cx in (459, 512, 565):
        star = [
            (cx + (13 if i % 2 == 0 else 5.5) * math.cos(-math.pi / 2 + i * math.pi / 5),
             192 + (13 if i % 2 == 0 else 5.5) * math.sin(-math.pi / 2 + i * math.pi / 5))
            for i in range(10)
        ]
        draw.polygon(points(star), fill="#dabd79")

    return image.convert("RGB").resize((SIZE, SIZE), Image.Resampling.LANCZOS)


if __name__ == "__main__":
    icon = render()
    destinations = [
        ROOT / "app/src/main/res/mipmap-xxxhdpi/ic_spatial_launcher.png",
        ROOT / "app/src/main/res/drawable/ic_launcher_foreground.png",
    ]
    for destination in destinations:
        icon.save(destination, optimize=True)
        print(destination.relative_to(ROOT))
