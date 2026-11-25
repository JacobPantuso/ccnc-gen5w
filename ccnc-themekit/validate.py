"""Checks a ccNC theme pack folder. Usage: python3 validate.py <pack-folder>  (prints problems; exit 1 on any error)"""
import json, os, re, struct, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
ICONS = set(open(os.path.join(ROOT, 'icon-names.txt')).read().split())
COLOR = re.compile(r'^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$')
errors, warnings = [], []

def err(m): errors.append(m)
def warn(m): warnings.append(m)

def jpeg_size(p):
    with open(p, 'rb') as f:
        if f.read(2) != b'\xff\xd8': return None
        while True:
            b = f.read(1)
            while b and b != b'\xff': b = f.read(1)
            while b == b'\xff': b = f.read(1)
            if not b: return None
            m = b[0]
            if m in (0xd8, 0x01) or 0xd0 <= m <= 0xd7: continue
            n = struct.unpack('>H', f.read(2))[0]
            if m in (0xc0, 0xc1, 0xc2, 0xc3):
                f.read(1); h, w = struct.unpack('>HH', f.read(4)); return w, h
            f.seek(n - 2, 1)

def png_size(p):
    with open(p, 'rb') as f:
        h = f.read(24)
    if h[:8] != b'\x89PNG\r\n\x1a\n': return None
    return struct.unpack('>II', h[16:24])

def main(d):
    j = os.path.join(d, 'theme.json')
    if not os.path.isfile(j): err('theme.json missing'); return
    try: t = json.load(open(j, encoding='utf-8'))
    except Exception as e: err('theme.json is not valid JSON: %s' % e); return
    pid = t.get('id', '')
    if not re.match(r'^[a-z0-9][a-z0-9_-]{1,31}$', pid): err('id must be 2-32 chars of a-z 0-9 _ - (got %r)' % pid)
    for k in ('name', 'author', 'version'):
        if not isinstance(t.get(k), str) or not t[k].strip(): err('%s is required' % k)
    if t.get('version') and not re.match(r'^\d+(\.\d+)*$', t['version']): err('version must be dotted numbers, e.g. 1.0')
    if len(t.get('name', '')) > 32: err('name is longer than 32 characters')
    if len(t.get('author', '')) > 32: err('author is longer than 32 characters')
    gh = t.get('github')
    if gh is not None and not re.match(r'^[A-Za-z0-9-]{1,39}$', str(gh)): err('github must be your GitHub username (letters, numbers, -)')
    if len(t.get('description', '') or '') > 80: err('description is longer than 80 characters')
    brands = t.get('brands', [])
    if not isinstance(brands, list) or not set(brands) <= {'hyundai', 'kia'}: err('brands must be a list of "hyundai" / "kia"')
    c = t.get('colors', {})
    bg = c.get('background')
    if not (isinstance(bg, list) and len(bg) == 4): err('colors.background must be 4 colours')
    for k, v in c.items():
        for x in (v if isinstance(v, list) else [v]):
            if not isinstance(x, str) or not COLOR.match(x): err('colors.%s: %r is not #rrggbb / #aarrggbb' % (k, x))
    total = 0
    w = t.get('wallpaper')
    if w:
        p = os.path.join(d, w)
        if not os.path.isfile(p): err('wallpaper %s missing' % w)
        else:
            s = jpeg_size(p)
            if s != (1920, 720): err('wallpaper must be a 1920x720 JPEG (got %s)' % (s,))
            if os.path.getsize(p) > 2 * 1024 * 1024: err('wallpaper is over 2 MB')
            total += os.path.getsize(p)
    else:
        warn('no wallpaper: ccNC draws the colour gradient')
    ic = t.get('icons')
    if ic:
        p = os.path.join(d, ic)
        if not os.path.isdir(p): err('icons folder %s missing' % ic)
        else:
            for f in sorted(os.listdir(p)):
                fp = os.path.join(p, f)
                if f.startswith('.'): continue
                n = f[:-4] if f.endswith('.png') else None
                if n not in ICONS: err('icons/%s: not a ccNC icon name (see icon-names.txt)' % f); continue
                if png_size(fp) != (90, 90): err('icons/%s must be a 90x90 PNG' % f)
                if os.path.getsize(fp) > 200 * 1024: err('icons/%s is over 200 KB' % f)
                total += os.path.getsize(fp)
    for k in ('boot_video', 'shutdown_video'):
        v = t.get(k)
        if v:
            p = os.path.join(d, v)
            if not os.path.isfile(p) or not v.lower().endswith('.mp4'): err('%s %s missing or not .mp4' % (k, v))
            elif os.path.getsize(p) > 30 * 1024 * 1024: err('%s is over 30 MB' % k)
            else: total += os.path.getsize(p)
    if total > 60 * 1024 * 1024: err('pack is over 60 MB')

if __name__ == '__main__':
    main(sys.argv[1])
    for w in warnings: print('warning:', w)
    for e in errors: print('ERROR:', e)
    print('OK' if not errors else '%d error(s)' % len(errors))
    sys.exit(1 if errors else 0)
