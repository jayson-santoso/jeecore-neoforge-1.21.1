"""Rebuild the small, pixel-aligned spore assets without imaging dependencies."""
from pathlib import Path
import gzip
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]


def png(path, pixels):
    height, width = len(pixels), len(pixels[0])
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    raw = b''.join(b'\0' + bytes(c for pixel in row for c in pixel) for row in pixels)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


transparent = (0, 0, 0, 0)
texture = [[transparent for _ in range(32)] for _ in range(32)]
core = [(181, 205, 112, 255), (210, 225, 148, 255), (152, 181, 89, 255)]
for y in range(6):
    for x in range(12):
        texture[y][x] = core[(x + 2 * y) % 3]
for y in range(8, 11):
    for x in range(8):
        texture[y][x] = (107, 88, 55, 255) if (x + y) % 3 else (133, 115, 68, 255)
for y in range(5):
    for x in range(3):
        if y not in (0, 4) or x == 1:
            texture[y][12 + x] = (99, 135, 65, 255) if x != 1 else (148, 172, 86, 255)
png(ROOT / 'src/main/resources/assets/jeecore/textures/entity/dryad_spore.png', texture)

icon = [[transparent for _ in range(16)] for _ in range(16)]
silhouette = [
    '................',
    '.......ll.......',
    '......llll......',
    '...l..lgll..l...',
    '..lll.lggl.lll..',
    '..llglgggglgll..',
    '...lgggyygggl...',
    '...lggyyyyggl...',
    '...lggyyyyggl...',
    '....lggyyggl....',
    '.....lggggl.....',
    '.....llggll.....',
    '......bbbb......',
    '.......bb.......',
    '................',
    '................',
]
palette = {'.': transparent, 'l': (73, 103, 48, 255), 'g': (142, 176, 84, 255),
           'y': (222, 223, 136, 255), 'b': (124, 99, 59, 255)}
for y, row in enumerate(silhouette):
    for x, symbol in enumerate(row):
        icon[y][x] = palette[symbol]
png(ROOT / 'src/main/resources/assets/jeecore/textures/mob_effect/spore_guard.png', icon)

# A tiny empty structure for development-only in-world regression tests.
def name(value):
    data = value.encode('utf8')
    return struct.pack('>H', len(data)) + data


def tag(kind, key, data):
    return bytes([kind]) + name(key) + data


def integer(value):
    return struct.pack('>i', value)


structure = b'\x0a\0\0' + tag(3, 'DataVersion', integer(3955))
structure += tag(9, 'size', b'\x03' + integer(3) + integer(12) + integer(6) + integer(12))
structure += tag(9, 'palette', b'\x0a' + integer(1) + tag(8, 'Name', name('minecraft:air')) + b'\0')
structure += tag(9, 'blocks', b'\x0a' + integer(0))
structure += tag(9, 'entities', b'\x0a' + integer(0)) + b'\0'
template = ROOT / 'src/gameTest/resources/data/jeecore/structure/spore_empty.nbt'
template.parent.mkdir(parents=True, exist_ok=True)
with gzip.GzipFile(filename=str(template), mode='wb', mtime=0) as out:
    out.write(structure)

print('Generated spore texture, effect icon, and development test structure.')
