"""Builds the online ccNC Theme Store from built theme packs.

    python3 -I store/mkstore.py [pack-id ...]      (default: every pack in packs/ with a built zip in dist/)

Output in store/out/, ready to upload as the assets of one GitHub release (ccNC Home reads
https://github.com/<owner>/<repo>/releases/latest/download/themes.json and resolves the other names against it):
  themes.json       {"format": 1, "generated": ..., "themes": [{id, name, author, version, description, brands, light,
                    accent, size, sha256, zip, preview}]}
  themes.json.sig   base64 RSA-SHA256 (PKCS#1 v1.5) signature of themes.json, made with keys/ccnc-themes-store.pem;
                    ccNC Home only trusts an index whose signature checks out against its built-in public key.
  <id>-<version>.zip  the pack's NEMESIS package zip (the same file installs from USB; Home unpacks files/theme/).
  <id>.jpg          960x360 preview.
"""
import base64, datetime, hashlib, json, os, shutil, subprocess, sys

KIT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KEY = os.path.join(os.path.dirname(KIT), 'keys', 'ccnc-themes-store.pem')
OUT = os.path.join(KIT, 'store', 'out')


def main(ids):
    packs = os.path.join(KIT, 'packs')
    if not ids:
        ids = sorted(d for d in os.listdir(packs) if os.path.isfile(os.path.join(packs, d, 'theme.json')))
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        os.remove(os.path.join(OUT, f))
    themes = []
    for pid in ids:
        t = json.load(open(os.path.join(packs, pid, 'theme.json'), encoding='utf-8'))
        src = os.path.join(KIT, 'dist', 'ccnc.theme.%s-%s-nemesis-package.zip' % (pid, t['version']))
        if not os.path.isfile(src):
            sys.exit('%s: build it first (./mktheme.sh packs/%s)' % (pid, pid))
        name = '%s-%s.zip' % (pid, t['version'])
        shutil.copy(src, os.path.join(OUT, name))
        data = open(src, 'rb').read()
        prev = None
        pv = os.path.join(packs, pid, 'preview.png')
        if os.path.isfile(pv):
            prev = pid + '.jpg'
            subprocess.check_call(['sips', '-s', 'format', 'jpeg', '-s', 'formatOptions', '82', '-z', '360', '960', pv,
                                   '--out', os.path.join(OUT, prev)], stdout=subprocess.DEVNULL)
        themes.append(dict(id=pid, name=t['name'], author=t.get('author', ''), github=t.get('github') or '', version=t['version'],
                           description=t.get('description', ''), brands=t.get('brands', []), light=bool(t.get('light')),
                           accent=t.get('colors', {}).get('accent', ''), size=len(data),
                           sha256=hashlib.sha256(data).hexdigest(), zip=name, preview=prev))
    index = dict(format=1, generated=datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
                 themes=themes)
    raw = json.dumps(index, indent=1, ensure_ascii=False).encode('utf-8')
    open(os.path.join(OUT, 'themes.json'), 'wb').write(raw)
    sig = subprocess.check_output(['openssl', 'dgst', '-sha256', '-sign', KEY, os.path.join(OUT, 'themes.json')])
    open(os.path.join(OUT, 'themes.json.sig'), 'w').write(base64.b64encode(sig).decode())
    tmp = os.path.join(OUT, '.sig.bin')
    open(tmp, 'wb').write(sig)
    try:  # check it the way Home will: against the public key alone
        subprocess.check_call(['openssl', 'dgst', '-sha256', '-verify', KEY.replace('.pem', '.pub.pem'), '-signature',
                               tmp, os.path.join(OUT, 'themes.json')], stdout=subprocess.DEVNULL)
    finally:
        os.remove(tmp)
    print('%d themes -> %s (signature verified)' % (len(themes), OUT))


if __name__ == '__main__':
    main(sys.argv[1:])
