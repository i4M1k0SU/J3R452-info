# J3R452 実機調査結果

2026-10-27 に、SCM SCR3310 に挿したブランクカード 1 枚で測定した。
仕様書は [specs.md](specs.md)。

個体を特定しうる値 (CPLC のシリアル・バッチ・製造日・プリパーソナライザ) は伏せてある。
型番・ベンダ・既知の鍵・各種 AID・測定値は、個体に依存しないためそのまま載せる。

## 基本情報

| 項目 | 値 | 備考 |
|---|---|---|
| ATR | `3B F8 13 00 00 81 31 FE 45 4A 43 4F 50 76 32 34 31 B7` | ヒストリカルバイトは `JCOPv241`。仕様にある "defaulted ATR" で、型番共通の値。OS の実バージョンとは無関係 |
| プロトコル | T=1 | |
| ISD AID | `A000000151000000` | ライフサイクルは **OP_READY** |
| GP バージョン | 2.3 | Card Recognition Data |
| Java Card | v3 (OID 1.3.6.1.4.1.42.2.110.1.3) | 3.0.5 API でビルドした CAP がロードできた |
| `JCSystem.getVersion()` | `0x0300` | |
| `int` 型 (32bit) | **サポートあり** | ant-javacard の `ints="true"` (コンバータ `-i`) を付けた CAP がロード・実行できた |
| セキュアチャネル | SCP03 i=10。カードは i=00/10/20/60/70 と AES-128/192/256 に対応 | |
| ISD 鍵 | KVN=0x01、ID 1/2/3、AES-128 | **既定鍵 `404142434445464748494A4B4C4D4E4F` (ENC/MAC/DEK 共通) で認証できた** |
| LFDB ハッシュ | SHA-256 | |

### CPLC

個体固有のフィールド (製造日・シリアル・バッチ識別・プリパーソナライザ) は伏せる。

| フィールド | 値 | 意味 |
|---|---|---|
| ICFabricator | 4790 | NXP |
| ICType | **D600** | P71D600 (仕様と一致) |
| OperatingSystemID | 4700 | NXP JCOP |
| ICFabricationDate | *(個体固有・非掲載)* | |
| ICSerialNumber | *(個体固有・非掲載)* | |
| ICBatchIdentifier | *(個体固有・非掲載)* | |
| ICPrePersonalizer | *(個体固有・非掲載)* | |

### 空きメモリ

| 種別 | 空き容量 | 測定方法 |
|---|---|---|
| NVM | 192,608〜193,176 B (≒ 188 KB) | `GET DATA FF21`。アプレットが無い状態で、smoketest のインストール前と削除後に測定 |
| NVM | 190,716 B | smoketest をインストールした状態で、アプレット内の `getAvailableMemory` で測定 |
| RAM (CLEAR_ON_RESET) | 2,471 B | アプレット内で測定 |
| RAM (CLEAR_ON_DESELECT) | 2,464 B | アプレット内で測定 |

仕様の "450KB Flash" は OS 領域を含む値である。アプレットに使える NVM は約 188 KB だった。

### 出荷時にロード済みのもの

- APP `D276000085304A434F900001` (NXP JCOP 系、SELECTABLE)。
  **リセット時選択 (CardReset = デフォルト選択) 特権を持つ出荷時の既定アプレット**で、
  電源投入後に SELECT なしで届く APDU はこのアプレットが処理していた。
  アプレットのインスタンスは **ISD 既定鍵で削除できた** (ROM の PKG 自体は残る)。
  削除すると CardReset 特権は ISD へ自動的に戻る (下記)。
- PKG `A0000001515350` (ISD/SSD)、`D276000085304A434F9000`
- PKG `A0000003965453...` × 6 (NXP)
- PKG `A00000000316`、`A00000000310` (Visa RID の EMV 系)

ユーザーがインストールしたアプレットは無い。

### リセット時選択 (CardReset / デフォルト選択) 特権

SELECT を送らず独自クラス (CLA=`8x`/`9x`) のコマンドを直接送ってくる相手に応答するには、自作アプレットに**CardReset 特権**を与えて「リセット後に暗黙選択される」状態にする必要がある。

- 既定選択アプレットは常に 1 つだけ。出荷時は上記 `D276…900001` が保持する。消すと ISD に移る。
- **付与は INSTALL [for install] 内で指定する**必要がある (`gp --install <cap> --privs CardReset`)。
  インストール済みアプレットへ後から付ける `gp --make-default` / `--default` は、本カードでは
  INSTALL [for make selectable] が **`0x6A80`** を返して失敗する (既存保持者からの移し替えは不可)。
- 新規インストール時に CardReset を指定すると、ISD が持っていた同特権を奪って移し替えられた。

## JCOP Extended API (R1.3.4)

[extended/JCOPx_API-R1.3.4.jar](../extended/JCOPx_API-R1.3.4.jar) には `.exp` が同梱されている。
ビルド時は ant-javacard の `<import jar="..."/>` だけでリンクできる。
主なパッケージは次のとおり。

| パッケージ | 版 | AID |
|---|---|---|
| com.nxp.id.jcopx.security | 1.24 | `D276000085304A434F5058` |
| com.nxp.id.jcopx.util | 1.24 | `D276000085304A434F5059` |
| com.nxp.id.jcopx.system | 2.3 | `D276000085304A434F5077` |
| com.nxp.id.jcopx.math | 1.3 | `D276000085304A434F506F` |
| com.nxp.id.jcopx.securechannel | 1.0 | `D276000085304A434F507C` |

security v1.24 をリンクした CAP がこのカードでロード・インストールできた。

ビルド時に "Export file ... is in an older export file format" という警告が出る。
JCOPx の `.exp` が旧フォーマットであるためで、動作には影響しない。

### Ed25519 / X25519 / X448 の動作確認結果 (重要)

[smoketest](../smoketest) で実機確認した。
**どのアルゴリズムも、カードのバイト順は RFC とは逆 (ビッグエンディアン) だった。**

| 機能 | API | 結果 | ホスト側 (RFC 形式) との変換 |
|---|---|---|---|
| Ed25519 鍵生成 | `KeyBuilderX.buildKey(ALG_TYPE_ED25519_PRIVATE / _PUBLIC, ...)` → `KeyBuilderX.genKeyPair` | OK | `getW()` の 32 B を**逆順**にすると RFC 8032 の公開鍵 A |
| Ed25519 署名 | `CryptoBaseX.sign(priv, ALG_ED25519PH_SHA_512, msg, ...)` | OK (0/3/64/200 B) | 64 B の出力を R(32) と S(32) に分け、**それぞれ逆順**にすると RFC 8032 の署名 R‖S |
| X25519 | `ALG_TYPE_MONT_DH_25519_*` + `CryptoBaseX.keyExchange(..., ALG_MONT_DH_25519, ...)` | OK | 公開鍵 (`getW`/`setW`) と共有秘密は、どちらも RFC 7748 の**逆順** |
| X448 | `ALG_TYPE_MONT_DH_448_*` + `ALG_MONT_DH_448` | OK | X25519 と同じく逆順 (56 B) |

注意点:

- `ALG_ED25519PH_SHA_512` という名前だが、出力は **Ed25519ph ではなく pure Ed25519 (PureEdDSA)** である。
  - カードには生のメッセージを渡す。
  - Ed25519ph (dom2 + SHA-512 の prehash) としては検証が通らなかった。
  - Python `cryptography` の `Ed25519PublicKey.verify` で、バイト順を変換するだけで検証できる。
- Ed25519 の署名は秘密鍵オブジェクトだけで計算できる。公開鍵は署名 API に渡さない。
- 未対応のアルゴリズムでは、`buildKey` が `CryptoException.NO_SUCH_ALGORITHM` を投げる。
  - このカードでは Ed25519・X25519・X448 の 3 つとも生成できた。
