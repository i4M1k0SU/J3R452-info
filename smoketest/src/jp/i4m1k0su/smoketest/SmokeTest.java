package jp.i4m1k0su.smoketest;

import com.nxp.id.jcopx.security.CryptoBaseX;
import com.nxp.id.jcopx.security.ECPrivateKeyWithPredefinedParameters;
import com.nxp.id.jcopx.security.ECPublicKeyWithPredefinedParameters;
import com.nxp.id.jcopx.security.KeyBuilderX;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.CryptoException;

/**
 * 開発環境と JCOP Extended API (Ed25519 / X25519 / X448) の実機動作確認用アプレット。
 *
 * CLA=80
 *   INS 00          INFO          -> JC版(2) | 機能ビット(1) | 空き NVM(4) | 空き RAM RESET(4) | 空き RAM DESELECT(4) | APDU.getProtocol()(1)
 *   INS 10          Ed25519 鍵生成 -> 公開鍵
 *   INS 11          Ed25519 署名   data=メッセージ -> 署名(64)
 *   INS 20 P1=curve DH 鍵生成      -> 公開鍵                 (curve: 00=X25519, 01=X448)
 *   INS 21 P1=curve DH 共有秘密    data=相手公開鍵 -> 共有秘密
 * CryptoException は SW 6Fxx (xx=reason) で返す。
 */
public class SmokeTest extends Applet {
    private static final byte CLA = (byte) 0x80;
    private static final byte INS_INFO = (byte) 0x00;
    private static final byte INS_ED25519_GENKEY = (byte) 0x10;
    private static final byte INS_ED25519_SIGN = (byte) 0x11;
    private static final byte INS_DH_GENKEY = (byte) 0x20;
    private static final byte INS_DH_SECRET = (byte) 0x21;

    private static final byte CURVE_25519 = (byte) 0x00;
    private static final byte CURVE_448 = (byte) 0x01;

    private static final byte CAP_ED25519 = (byte) 0x01;
    private static final byte CAP_X25519 = (byte) 0x02;
    private static final byte CAP_X448 = (byte) 0x04;

    private ECPrivateKeyWithPredefinedParameters edPriv;
    private ECPublicKeyWithPredefinedParameters edPub;
    private ECPrivateKeyWithPredefinedParameters x25519Priv;
    private ECPublicKeyWithPredefinedParameters x25519Pub;
    private ECPublicKeyWithPredefinedParameters x25519Peer;
    private ECPrivateKeyWithPredefinedParameters x448Priv;
    private ECPublicKeyWithPredefinedParameters x448Pub;
    private ECPublicKeyWithPredefinedParameters x448Peer;
    private byte capabilities;

    private final byte[] scratch;
    private final short[] mem;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new SmokeTest().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    private SmokeTest() {
        scratch = JCSystem.makeTransientByteArray((short) 256, JCSystem.CLEAR_ON_DESELECT);
        mem = JCSystem.makeTransientShortArray((short) 2, JCSystem.CLEAR_ON_DESELECT);
        // 未対応のアルゴリズムがあってもインストール自体は成功させ、INFO の機能ビットで報告する
        try {
            edPriv = (ECPrivateKeyWithPredefinedParameters) KeyBuilderX.buildKey(
                    KeyBuilderX.ALG_TYPE_ED25519_PRIVATE, JCSystem.MEMORY_TYPE_PERSISTENT);
            edPub = (ECPublicKeyWithPredefinedParameters) KeyBuilderX.buildKey(
                    KeyBuilderX.ALG_TYPE_ED25519_PUBLIC, JCSystem.MEMORY_TYPE_PERSISTENT);
            capabilities |= CAP_ED25519;
        } catch (CryptoException e) {
        }
        try {
            x25519Priv = (ECPrivateKeyWithPredefinedParameters) KeyBuilderX.buildKey(
                    KeyBuilderX.ALG_TYPE_MONT_DH_25519_PRIVATE, JCSystem.MEMORY_TYPE_PERSISTENT);
            x25519Pub = (ECPublicKeyWithPredefinedParameters) KeyBuilderX.buildKey(
                    KeyBuilderX.ALG_TYPE_MONT_DH_25519_PUBLIC, JCSystem.MEMORY_TYPE_PERSISTENT);
            x25519Peer = (ECPublicKeyWithPredefinedParameters) KeyBuilderX.buildKey(
                    KeyBuilderX.ALG_TYPE_MONT_DH_25519_PUBLIC, JCSystem.MEMORY_TYPE_PERSISTENT);
            capabilities |= CAP_X25519;
        } catch (CryptoException e) {
        }
        try {
            x448Priv = (ECPrivateKeyWithPredefinedParameters) KeyBuilderX.buildKey(
                    KeyBuilderX.ALG_TYPE_MONT_DH_448_PRIVATE, JCSystem.MEMORY_TYPE_PERSISTENT);
            x448Pub = (ECPublicKeyWithPredefinedParameters) KeyBuilderX.buildKey(
                    KeyBuilderX.ALG_TYPE_MONT_DH_448_PUBLIC, JCSystem.MEMORY_TYPE_PERSISTENT);
            x448Peer = (ECPublicKeyWithPredefinedParameters) KeyBuilderX.buildKey(
                    KeyBuilderX.ALG_TYPE_MONT_DH_448_PUBLIC, JCSystem.MEMORY_TYPE_PERSISTENT);
            capabilities |= CAP_X448;
        } catch (CryptoException e) {
        }
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        try {
            switch (buf[ISO7816.OFFSET_INS]) {
            case INS_INFO:
                info(apdu, buf);
                break;
            case INS_ED25519_GENKEY:
                require(CAP_ED25519);
                KeyBuilderX.genKeyPair(edPriv, edPub);
                apdu.setOutgoingAndSend((short) 0, edPub.getW(buf, (short) 0));
                break;
            case INS_ED25519_SIGN:
                require(CAP_ED25519);
                if (!edPriv.isInitialized()) {
                    ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
                }
                short len = apdu.setIncomingAndReceive();
                Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, scratch, (short) 0, len);
                apdu.setOutgoingAndSend((short) 0, CryptoBaseX.sign(
                        edPriv, CryptoBaseX.ALG_ED25519PH_SHA_512, scratch, (short) 0, len, buf, (short) 0));
                break;
            case INS_DH_GENKEY:
                dhGenKey(apdu, buf);
                break;
            case INS_DH_SECRET:
                dhSecret(apdu, buf);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
            }
        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F00 | (e.getReason() & 0xFF)));
        }
    }

    private void info(APDU apdu, byte[] buf) {
        short off = Util.setShort(buf, (short) 0, JCSystem.getVersion());
        buf[off++] = capabilities;
        off = putAvailableMemory(buf, off, JCSystem.MEMORY_TYPE_PERSISTENT);
        off = putAvailableMemory(buf, off, JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
        off = putAvailableMemory(buf, off, JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT);
        buf[off++] = APDU.getProtocol(); // 上位 4bit=媒体 (00:接触 80:非接触 Type A 90:Type B)、下位 4bit=T=0/1
        apdu.setOutgoingAndSend((short) 0, off);
    }

    private short putAvailableMemory(byte[] buf, short off, byte type) {
        JCSystem.getAvailableMemory(mem, (short) 0, type);
        off = Util.setShort(buf, off, mem[0]);
        return Util.setShort(buf, off, mem[1]);
    }

    private void dhGenKey(APDU apdu, byte[] buf) {
        if (buf[ISO7816.OFFSET_P1] == CURVE_25519) {
            require(CAP_X25519);
            KeyBuilderX.genKeyPair(x25519Priv, x25519Pub);
            apdu.setOutgoingAndSend((short) 0, x25519Pub.getW(buf, (short) 0));
        } else if (buf[ISO7816.OFFSET_P1] == CURVE_448) {
            require(CAP_X448);
            KeyBuilderX.genKeyPair(x448Priv, x448Pub);
            apdu.setOutgoingAndSend((short) 0, x448Pub.getW(buf, (short) 0));
        } else {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }

    private void dhSecret(APDU apdu, byte[] buf) {
        byte curve = buf[ISO7816.OFFSET_P1];
        short len = apdu.setIncomingAndReceive();
        short secretLen;
        if (curve == CURVE_25519) {
            require(CAP_X25519);
            x25519Peer.setW(buf, ISO7816.OFFSET_CDATA, len);
            secretLen = CryptoBaseX.keyExchange(x25519Priv, x25519Peer, CryptoBaseX.ALG_MONT_DH_25519, buf, (short) 0);
        } else if (curve == CURVE_448) {
            require(CAP_X448);
            x448Peer.setW(buf, ISO7816.OFFSET_CDATA, len);
            secretLen = CryptoBaseX.keyExchange(x448Priv, x448Peer, CryptoBaseX.ALG_MONT_DH_448, buf, (short) 0);
        } else {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
            return;
        }
        apdu.setOutgoingAndSend((short) 0, secretLen);
    }

    private void require(byte capability) {
        if ((capabilities & capability) == 0) {
            ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
        }
    }
}
