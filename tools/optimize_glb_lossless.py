#!/usr/bin/env python3
"""Optimize standalone GLB exports without changing decoded asset data.

No runtime decoder or glTF extension is added. Non-image buffer views remain
byte-identical. PNGs retain their complete RGBA pixels and ancillary chunks.
JSON arrays/indices, accessors, materials, nodes and transforms are unchanged.
Run against an exported directory before Git submission, never at cloud build.
Requires Pillow. Refuses external buffers/images and glTF extensions.
"""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import shutil
import struct
import zlib

from PIL import Image


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_glb(data):
    require(len(data) >= 28, 'Incomplete GLB')
    magic, version, size = struct.unpack_from('<III', data)
    require((magic, version, size) == (0x46546C67, 2, len(data)), 'Invalid GLB header')
    chunks = []
    pos = 12
    while pos < len(data):
        length, kind = struct.unpack_from('<II', data, pos)
        require(length % 4 == 0 and pos + 8 + length <= len(data), 'Invalid GLB chunk')
        chunks.append((kind, data[pos + 8:pos + 8 + length]))
        pos += 8 + length
    require([kind for kind, _ in chunks] == [0x4E4F534A, 0x004E4942], 'Expected JSON and BIN chunks')
    doc = json.loads(chunks[0][1])
    require(not doc.get('extensionsUsed') and not doc.get('extensionsRequired'), 'Extensions are not supported')
    require(len(doc.get('buffers', [])) == 1 and 'uri' not in doc['buffers'][0], 'Expected one embedded buffer')
    require(0 <= len(chunks[1][1]) - doc['buffers'][0]['byteLength'] <= 3, 'Invalid buffer length')
    for view in doc.get('bufferViews', []):
        require(view.get('buffer', 0) == 0, 'External buffer')
        require(view.get('byteOffset', 0) + view['byteLength'] <= doc['buffers'][0]['byteLength'], 'Out-of-bounds buffer view')
    return doc, chunks[1][1]


def png_chunks(data):
    require(data[:8] == b'\x89PNG\r\n\x1a\n', 'Expected PNG')
    chunks = []
    pos = 8
    while pos + 12 <= len(data):
        size = struct.unpack_from('>I', data, pos)[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + size]
        require(pos + size + 12 <= len(data), 'Truncated PNG chunk')
        crc = struct.unpack_from('>I', data, pos + size + 8)[0]
        require(zlib.crc32(kind + payload) == crc, 'Invalid PNG CRC')
        chunks.append((kind, payload))
        pos += size + 12
        if kind == b'IEND':
            require(not any(data[pos:]), 'Nonzero bytes after PNG')
            return chunks
    raise ValueError('PNG IEND missing')


def encode_png_chunks(chunks):
    return b'\x89PNG\r\n\x1a\n' + b''.join(
        struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload))
        for kind, payload in chunks
    )


def png_semantics(data):
    chunks = png_chunks(data)
    im = Image.open(io.BytesIO(data))
    im.load()
    return (im.size, im.convert('RGBA').tobytes(),
            [(kind, payload) for kind, payload in chunks if kind not in (b'IHDR', b'IDAT', b'IEND')])


def optimize_png(data):
    chunks = png_chunks(data)
    ihdr = chunks[0][1]
    require(chunks[0][0] == b'IHDR' and ihdr[8] == 8 and ihdr[9] in (2, 6), 'Expected 8-bit RGB/RGBA PNG')
    im = Image.open(io.BytesIO(data))
    im.load()
    clean = Image.frombytes(im.mode, im.size, im.tobytes())
    modes = [clean]
    dependent_chunks = {b'sBIT', b'bKGD', b'tRNS'}
    if im.mode == 'RGBA' and im.getextrema()[3] == (255, 255) and not any(kind in dependent_chunks for kind, _ in chunks):
        modes.append(clean.convert('RGB'))
    before, after = [], []
    idat_seen = False
    for kind, payload in chunks:
        if kind == b'IDAT':
            idat_seen = True
        elif kind not in (b'IHDR', b'IEND'):
            require(kind[0] & 32, 'Unsupported critical PNG chunk')
            (after if idat_seen else before).append((kind, payload))
    best = data
    for variant in modes:
        stream = io.BytesIO()
        variant.save(stream, format='PNG', optimize=True, compress_level=9)
        encoded = png_chunks(stream.getvalue())
        result = encode_png_chunks([encoded[0], *before,
                                    *[chunk for chunk in encoded if chunk[0] == b'IDAT'],
                                    *after, encoded[-1]])
        require(png_semantics(data) == png_semantics(result), 'PNG pixel/color metadata changed')
        if len(result) < len(best):
            best = result
    return best


def view_bytes(doc, binary, index):
    view = doc['bufferViews'][index]
    start = view.get('byteOffset', 0)
    return binary[start:start + view['byteLength']]


def normalized_doc(doc):
    result = copy.deepcopy(doc)
    result['buffers'][0].pop('byteLength')
    for view in result.get('bufferViews', []):
        view.pop('byteOffset', None)
        view.pop('byteLength')
    return result


def verify(original, result):
    a, ab = read_glb(original)
    b, bb = read_glb(result)
    require(normalized_doc(a) == normalized_doc(b), 'GLB JSON semantics changed')
    images = {im['bufferView'] for im in a.get('images', [])}
    for i in range(len(a.get('bufferViews', []))):
        av, bv = view_bytes(a, ab, i), view_bytes(b, bb, i)
        require(b['bufferViews'][i].get('byteOffset', 0) % 4 == 0, 'Unaligned buffer view')
        if i in images:
            require(png_semantics(av) == png_semantics(bv), 'Image interpretation changed')
        else:
            require(av == bv, 'Non-image buffer data changed')
    # Unchanged accessor declarations + byte-identical underlying views prove
    # each decoded accessor, including sparse/interleaved data, is unchanged.
    for accessor in a.get('accessors', []):
        if 'bufferView' in accessor:
            require(accessor['bufferView'] not in images, 'Accessor references image bytes')
    return {'bufferViews': len(a.get('bufferViews', [])),
            'accessors': len(a.get('accessors', [])), 'images': len(a.get('images', []))}


def optimize_glb(data, png_cache):
    original, binary = read_glb(data)
    doc = copy.deepcopy(original)
    image_views = set()
    for im in doc.get('images', []):
        require('bufferView' in im and 'uri' not in im and im.get('mimeType') == 'image/png', 'Expected embedded PNG')
        image_views.add(im['bufferView'])
    out = bytearray()
    seen = {}
    for i, view in enumerate(doc.get('bufferViews', [])):
        blob = view_bytes(original, binary, i)
        if i in image_views:
            digest = hashlib.sha256(blob).digest()
            if digest not in png_cache:
                png_cache[digest] = optimize_png(blob)
            blob = png_cache[digest]
        # Keep every bufferView index. Reuse identical bytes only within the
        # same usage class so image and attribute storage remain separate.
        key = (hashlib.sha256(blob).digest(), view.get('target'), view.get('byteStride'), i in image_views)
        if key in seen:
            offset, old_blob = seen[key]
            require(old_blob == blob, 'Hash collision')
        else:
            offset = len(out)
            out.extend(blob)
            out.extend(b'\0' * (-len(out) % 4))
            seen[key] = (offset, blob)
        view['byteOffset'] = offset
        view['byteLength'] = len(blob)
    doc['buffers'][0]['byteLength'] = len(out)
    encoded_json = json.dumps(doc, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode('utf-8')
    encoded_json += b' ' * (-len(encoded_json) % 4)
    result = struct.pack('<III', 0x46546C67, 2, 28 + len(encoded_json) + len(out))
    result += struct.pack('<II', len(encoded_json), 0x4E4F534A) + encoded_json
    result += struct.pack('<II', len(out), 0x004E4942) + out
    checks = verify(data, result)
    return result, checks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    require(args.input.resolve() != args.output.resolve(), 'Use a separate candidate directory')
    require(not args.output.resolve().is_relative_to(args.input.resolve()), 'Output must not be inside input')
    cache, rows = {}, []
    for path in sorted(args.input.rglob('*')):
        if not path.is_file():
            continue
        rel = path.relative_to(args.input)
        dest = args.output / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        if path.suffix.lower() != '.glb':
            shutil.copy2(path, dest)
            continue
        before = path.read_bytes()
        after, checks = optimize_glb(before, cache)
        require(len(after) <= len(before), 'Optimization increased asset size')
        dest.write_bytes(after)
        rows.append({'path': str(rel), 'beforeBytes': len(before), 'afterBytes': len(after),
                     'beforeSHA256': hashlib.sha256(before).hexdigest(),
                     'afterSHA256': hashlib.sha256(after).hexdigest(), 'verified': checks})
    report = {'files': rows, 'fileCount': len(rows),
              'beforeBytes': sum(r['beforeBytes'] for r in rows),
              'afterBytes': sum(r['afterBytes'] for r in rows),
              'method': 'PNG lossless recompression and identical buffer-view storage deduplication; no extensions',
              'verification': 'All non-image views byte-identical; all accessors/JSON semantic data unchanged; all image RGBA pixels, dimensions and ancillary chunks identical'}
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps({k: v for k, v in report.items() if k != 'files'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
