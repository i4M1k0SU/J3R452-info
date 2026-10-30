# J3R452 Info

IC カード **J3R452** (JCOP 4.5 / Java Card 3.0.5 Classic、NXP P71D600) の実機調査記録とアプレット開発環境。実機で測定した仕様・挙動を `docs/` にまとめ、動作確認用のアプレットと共通ビルド環境を同梱する。

型番・ベンダ・既知の既定鍵・各種 AID・測定値は、個体に依存しないためそのまま載せる。

## 構成

| ディレクトリ | 内容 |
|---|---|
| `docs/` | 実機調査結果・開発環境・仕様 |
| `smoketest/` | 開発環境と JCOP Extended API (Ed25519 / X25519 / X448) の実機動作確認用アプレット |
| `tools/` | ビルド・カード管理ツール一式 (Ant / ant-javacard / GlobalPlatformPro / JC SDK) と環境スクリプト |
| `extended/` | NXP JCOP Extended API (R1.3.4)。**各自で入手する必要があり git には含めない** (下記) |

### ドキュメント

- [docs/specs.md](docs/specs.md) — カードの公称仕様
- [docs/card-info.md](docs/card-info.md) — 実機で測定した情報 (ATR・CPLC・メモリ・対応アルゴリズム・特権など)
- [docs/dev-environment.md](docs/dev-environment.md) — 開発環境の構成・セットアップ・使い方

## extended/ について

`extended/` には NXP の JCOP Extended API (R1.3.4) を置く。これは各自で入手する必要のある資料のため **git には含めない** (`.gitignore` で除外)。次のように配置する。

- `JCOPx_API-R1.3.4.jar` (ビルドに必須。`.exp` 同梱)
- `JCOPx_API-R1.3.4-doc/`・`CryptoBaseX.html` (API リファレンス、任意)

これが無いと `smoketest` はビルドできない (JCOP Extended API をリンクするため)。

## セットアップ

```powershell
pwsh -File tools/setup.ps1   # 初回のみ (Ant / ant-javacard / gp.jar / SDK / venv を取得。冪等)
. tools/env.ps1              # セッションごと (JAVA_HOME・PATH・gp 関数を設定)
```

前提: JDK 17 以上、git、Python 3.13 (Python install manager)。詳細は [docs/dev-environment.md](docs/dev-environment.md)。

## smoketest のビルドと実行

```powershell
cd smoketest
ant build       # build/smoketest.cap を生成 (オフカード検証込み)
ant install     # カードへインストール (既存は置き換え)
ant test        # 実機テスト (host/test_smoketest.py)
ant uninstall   # 削除
```

`smoketest` は Ed25519 / X25519 / X448 の鍵生成・署名・鍵共有を実機で実行し、ホスト側 (Python `cryptography`) と結果を突き合わせる。カードのバイト順が RFC とは逆である点など、判明した挙動は [docs/card-info.md](docs/card-info.md) に記載。

## 新しいアプレットを作る

`smoketest/` をリポジトリ直下にコピーして流用する。手順は [docs/dev-environment.md](docs/dev-environment.md#新しいプロダクトの作り方) を参照。

## 注意

- `extended/` の NXP 資料は本リポジトリに含めない。各自が正規に入手して配置する。
- カード鍵の変更 (`gp --lock`) や ISD のライフサイクル変更は元に戻せないことがある。開発中は OP_READY のままにする。
