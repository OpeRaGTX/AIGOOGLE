"""Wave L3: textures of the mod's own particles (8x8 frames): golden hearth sparks, ash embers, pale build dust. Original, drawn here."""
from PIL import Image


def _px(im, pts, color):
    for x, y in pts:
        if 0 <= x < 8 and 0 <= y < 8:
            im.putpixel((x, y), color + (255,))


def spark(frame):
    """A four-pointed twinkle that grows, peaks and fades (4 frames)."""
    im = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    core, mid, tip = (255, 244, 190), (255, 208, 96), (230, 150, 44)
    if frame == 0:
        _px(im, [(3, 3), (4, 3), (3, 4), (4, 4)], mid)
    elif frame == 1:
        _px(im, [(3, 3), (4, 3), (3, 4), (4, 4)], core)
        _px(im, [(3, 2), (4, 2), (3, 5), (4, 5), (2, 3), (2, 4), (5, 3), (5, 4)], mid)
    elif frame == 2:
        _px(im, [(3, 3), (4, 3), (3, 4), (4, 4)], core)
        _px(im, [(3, 2), (4, 2), (3, 5), (4, 5), (2, 3), (2, 4), (5, 3), (5, 4)], mid)
        _px(im, [(3, 1), (4, 1), (3, 6), (4, 6), (1, 3), (1, 4), (6, 3), (6, 4)], tip)
    else:
        _px(im, [(3, 3), (4, 4)], mid)
        _px(im, [(3, 1), (4, 6), (1, 4), (6, 3)], tip)
    return im


def ember(frame):
    """A glowing coal speck: orange core, dark red rim, a grey ash flake on the last frame (3 frames)."""
    im = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    if frame == 0:
        _px(im, [(3, 3), (4, 3), (3, 4), (4, 4)], (255, 170, 60)); _px(im, [(3, 2), (4, 5), (2, 4), (5, 3)], (190, 70, 30))
    elif frame == 1:
        _px(im, [(3, 3), (4, 4)], (255, 140, 40)); _px(im, [(4, 3), (3, 4), (3, 2), (4, 5), (2, 3), (5, 4)], (170, 60, 28))
    else:
        _px(im, [(3, 3), (4, 3), (3, 4)], (120, 112, 108)); _px(im, [(4, 4), (2, 3)], (84, 78, 76))
    return im


def dust(frame):
    """A soft round puff of pale dust that gets wider and airier (4 frames)."""
    im = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    r = [1.2, 1.8, 2.5, 3.2][frame]
    for y in range(8):
        for x in range(8):
            d = ((x - 3.5) ** 2 + (y - 3.5) ** 2) ** 0.5
            if d <= r:
                a = int(255 * (1 - d / (r + 0.6)) * (1.0 - frame * 0.18))
                shade = 232 - int(d * 10)
                im.putpixel((x, y), (shade, shade - 6, shade - 22, max(30, min(255, a))))
    return im


def run(gt):
    for i in range(4):
        gt.save(spark(i), f"particle/hearth_spark_{i}.png")
        gt.save(dust(i), f"particle/build_dust_{i}.png")
    for i in range(3):
        gt.save(ember(i), f"particle/ash_ember_{i}.png")
