"""Minimal dex/odex reader: string ids, type ids, method ids, class code offsets. Usage: dexinfo.py <odex> <query...>"""
import struct, sys

def uleb(b, o):
    r = s = 0
    while True:
        x = b[o]; o += 1
        r |= (x & 0x7f) << s; s += 7
        if x < 0x80: return r, o

class Dex:
    def __init__(self, path):
        raw = open(path, 'rb').read()
        self.raw = raw
        self.base = struct.unpack_from('<I', raw, 8)[0] if raw[:4] == b'dey\n' else 0
        b = self.b = raw[self.base:]
        (self.str_n, self.str_off, self.type_n, self.type_off, self.proto_n, self.proto_off,
         self.field_n, self.field_off, self.meth_n, self.meth_off, self.cls_n, self.cls_off) = struct.unpack_from('<12I', b, 0x38)
    def s(self, i):
        o = struct.unpack_from('<I', self.b, self.str_off + 4 * i)[0]
        n, o = uleb(self.b, o)
        e = self.b.index(b'\0', o)
        return self.b[o:e].decode('utf-8', 'replace')
    def t(self, i): return self.s(struct.unpack_from('<I', self.b, self.type_off + 4 * i)[0])
    def find_str(self, v):
        lo, hi = 0, self.str_n - 1
        for i in range(self.str_n):
            if self.s(i) == v: return i
        return -1
    def proto(self, i):
        sh, ret, par = struct.unpack_from('<3I', self.b, self.proto_off + 12 * i)
        ps = []
        if par:
            n = struct.unpack_from('<I', self.b, par)[0]
            ps = [self.t(struct.unpack_from('<H', self.b, par + 4 + 2 * k)[0]) for k in range(n)]
        return '(' + ''.join(ps) + ')' + self.t(ret)
    def meth(self, i):
        c, p, n = struct.unpack_from('<HHI', self.b, self.meth_off + 8 * i)
        return self.t(c) + '->' + self.s(n) + self.proto(p)
    def find_meth(self, sig):
        return [i for i in range(self.meth_n) if self.meth(i) == sig]
    def code_of(self, cls, name_proto):
        """dex-relative code_item offset of a method (direct or virtual) of class cls ('Lx/y;')."""
        for ci in range(self.cls_n):
            ent = struct.unpack_from('<8I', self.b, self.cls_off + 32 * ci)
            if self.t(ent[0]) != cls: continue
            o = ent[6]
            sf, o = uleb(self.b, o); inf, o = uleb(self.b, o); dm, o = uleb(self.b, o); vm, o = uleb(self.b, o)
            for _ in range(sf + inf):
                _, o = uleb(self.b, o); _, o = uleb(self.b, o)
            for cnt in (dm, vm):
                mi = 0
                for _ in range(cnt):
                    d, o = uleb(self.b, o); mi += d
                    _, o = uleb(self.b, o); co, o = uleb(self.b, o)
                    if self.meth(mi).split('->')[1] == name_proto: return co
        return None

if __name__ == '__main__':
    d = Dex(sys.argv[1])
    print('dex at', hex(d.base), 'strings', d.str_n, 'methods', d.meth_n)
    for q in sys.argv[2:]:
        if '->' in q: print(q, d.find_meth(q))
        else: print(repr(q), d.find_str(q))
