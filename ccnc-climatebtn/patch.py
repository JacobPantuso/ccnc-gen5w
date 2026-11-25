"""Patches HKMC_Climate.odex (unit CEEV.CAN.S5W_M.V014.010.250818, md5 5742c248...) so the stock climate service opens
ccNC Climate instead of its own screens. On this car (ClimateManager.nf() = new climate UI) every route ends in two
launchers of ClimateManager:
  aa(Z): starts activities.NewClimateControl  - the car's climate panel/key (onDatcStatus -> kE), launcher REQUEST_CLIMATE
  mD():  starts activities.SeatHeatVentPopup  - the seat key (CLIMATE_ACTIVITY flag 30 -> mB -> fV -> mC), REQUEST_FRONT_SHVU
Each one's explicit intent (new Intent(); setClassName(pkg, cls)) becomes new Intent(cls) used as an ACTION, which
ccNC Climate declares; flags, extras and startActivity are unchanged. The action strings already exist in the dex.
Usage: patch.py stock.odex out.odex"""
import struct, sys, zlib, hashlib
sys.path.insert(0, '.')
from dexinfo import Dex

src, out = sys.argv[1], sys.argv[2]
d = Dex(src)
raw = bytearray(d.raw)
CLS = 'Lcom/hkmc/telematics/service/apps/climate/ClimateManager;'
S_NCC = d.find_str('com.hkmc.telematics.service.apps.climate.activities.NewClimateControl')
S_SHVP = d.find_str('com.hkmc.telematics.service.apps.climate.activities.SeatHeatVentPopup')
M_INIT_S = d.find_meth('Landroid/content/Intent;-><init>(Ljava/lang/String;)V')[0]
assert (S_NCC, S_SHVP, M_INIT_S) == (0x973, 0x976, 0x58), (S_NCC, S_SHVP, M_INIT_S)

def units(co):
    return d.base + co + 16, struct.unpack_from('<I', d.b, co + 12)[0]

def put(base, at, words, expect):
    o = base + 2 * at
    have = list(struct.unpack_from('<%dH' % len(expect), raw, o))
    assert have == expect, ('unexpected code at unit 0x%x' % at, [hex(x) for x in have])
    assert len(words) == len(expect)
    struct.pack_into('<%dH' % len(words), raw, o, *words)

# 00..0c: new-instance v0,Intent; invoke-direct {v0} <init>(); const-string v1,pkg; const-string v2,cls;
#         invoke-virtual-quick {v0,v1,v2} setClassName; move-result-object v0
#   ->    new-instance v0,Intent; const-string v1,ACTION; invoke-direct {v0,v1} <init>(String); nop x6
def redirect(method, n_units, s_cls, action):
    b, n = units(d.code_of(CLS, method)); assert n == n_units, (method, n)
    put(b, 0x00,
        [0x0022, 0x001e, 0x011a, action, 0x2070, M_INIT_S, 0x0010, 0, 0, 0, 0, 0, 0],
        [0x0022, 0x001e, 0x1070, 0x0056, 0x0000, 0x011a, 0x096b, 0x021a, s_cls, 0x30f8, 0x0072, 0x0210, 0x000c])

redirect('aa(Z)V', 36, S_NCC, S_NCC)
redirect('mD()V', 25, S_SHVP, S_SHVP)

L = struct.unpack_from('<I', raw, d.base + 32)[0]
struct.pack_into('20s', raw, d.base + 12, hashlib.sha1(bytes(raw[d.base + 32:d.base + L])).digest())
struct.pack_into('<I', raw, d.base + 8, zlib.adler32(bytes(raw[d.base + 12:d.base + L])) & 0xffffffff)
open(out, 'wb').write(raw)
print('patched ->', out)
