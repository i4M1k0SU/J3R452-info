"""SmokeTest アプレットを実機で叩き、JCOP Extended API の結果をホスト側で検証する。

  ant install && ant test

実機で判明したバイト順 (docs/card-info.md 参照):
  - X25519/X448: 公開鍵 (u 座標) と共有秘密はビッグエンディアン。RFC 7748 (リトルエンディアン) とは逆順
  - Ed25519 (CryptoBaseX.ALG_ED25519PH_SHA_512): 中身は pure Ed25519。公開鍵と R, S をそれぞれ逆順にすると RFC 8032 形式
"""
import os
import sys

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric import ed25519, x448, x25519
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from smartcard.System import readers
from smartcard.util import toHexString

APPLET_AID = bytes.fromhex("F04A33523435320101")
CAP_ED25519, CAP_X25519, CAP_X448 = 0x01, 0x02, 0x04


class CardError(Exception):
    def __init__(self, sw):
        super().__init__(f"SW={sw:04X}")
        self.sw = sw


def connect():
    for r in readers():
        conn = r.createConnection()
        try:
            conn.connect()
        except Exception:
            continue
        print(f"reader: {r}\nATR:    {toHexString(conn.getATR())}")
        return conn
    sys.exit("カードが挿さったリーダが見つかりません")


def transmit(conn, cla, ins, p1=0, p2=0, data=b"", le=True):
    apdu = [cla, ins, p1, p2]
    if data:
        apdu += [len(data)] + list(data)
    if le:
        apdu += [0]
    resp, sw1, sw2 = conn.transmit(apdu)
    while sw1 == 0x61:
        more, sw1, sw2 = conn.transmit([0x00, 0xC0, 0x00, 0x00, sw2])
        resp += more
    sw = (sw1 << 8) | sw2
    if sw != 0x9000:
        raise CardError(sw)
    return bytes(resp)


def swap(b):
    """カード (ビッグエンディアン) <-> RFC 7748 / 8032 (リトルエンディアン)"""
    return b[::-1]


def ed25519_sig_from_card(sig):
    return swap(sig[:32]) + swap(sig[32:])


def raw_public(key):
    return key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)


def test_ed25519(conn):
    card_pub = transmit(conn, 0x80, 0x10)
    print(f"  公開鍵 カード形式 ({len(card_pub)}B): {card_pub.hex()}")
    pub = ed25519.Ed25519PublicKey.from_public_bytes(swap(card_pub))
    ok = True
    for msg in (b"", b"abc", os.urandom(64), os.urandom(200)):
        sig = transmit(conn, 0x80, 0x11, data=msg)
        try:
            pub.verify(ed25519_sig_from_card(sig), msg)
            result = "OK"
        except InvalidSignature:
            result, ok = "NG", False
        print(f"  msg {len(msg):3}B -> 署名 {len(sig)}B: {result}")
    return ok


def test_dh(conn, p1, private_type, public_type):
    card_pub = transmit(conn, 0x80, 0x20, p1)
    host = private_type.generate()
    secret = swap(transmit(conn, 0x80, 0x21, p1, data=swap(raw_public(host))))
    expected = host.exchange(public_type.from_public_bytes(swap(card_pub)))
    print(f"  公開鍵 カード形式 ({len(card_pub)}B): {card_pub.hex()}")
    print(f"  共有秘密 card={secret.hex()}")
    print(f"           host={expected.hex()}")
    return secret == expected


def main():
    conn = connect()
    transmit(conn, 0x00, 0xA4, 0x04, 0x00, APPLET_AID)
    info = transmit(conn, 0x80, 0x00)
    caps = info[2]
    mem = [int.from_bytes(info[i:i + 4], "big") for i in (3, 7, 11)]
    print(f"JCSystem.getVersion(): {info[:2].hex().upper()}")
    print(f"空き NVM: {mem[0]} B / RAM(RESET): {mem[1]} B / RAM(DESELECT): {mem[2]} B")
    print(f"機能: Ed25519={bool(caps & CAP_ED25519)} X25519={bool(caps & CAP_X25519)} X448={bool(caps & CAP_X448)}")
    proto = info[15]
    media = {0x00: "接触 (DEFAULT)", 0x80: "非接触 Type A", 0x90: "非接触 Type B", 0xA0: "USB"}.get(proto & 0xF0, "不明")
    print(f"APDU.getProtocol(): {proto:02X} ({media}, T={proto & 0x0F})")

    failed = []
    checks = [
        (CAP_ED25519, "Ed25519", lambda: test_ed25519(conn)),
        (CAP_X25519, "X25519", lambda: test_dh(conn, 0x00, x25519.X25519PrivateKey, x25519.X25519PublicKey)),
        (CAP_X448, "X448", lambda: test_dh(conn, 0x01, x448.X448PrivateKey, x448.X448PublicKey)),
    ]
    for cap, name, fn in checks:
        print(f"[{name}]")
        if not caps & cap:
            print("  未対応 (buildKey が CryptoException)")
            failed.append(name)
            continue
        try:
            ok = fn()
        except CardError as e:
            print(f"  カードエラー {e}")
            ok = False
        print(f"  => {'OK' if ok else 'NG'}")
        if not ok:
            failed.append(name)

    print("\n結果:", "すべて OK" if not failed else f"NG: {', '.join(failed)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
