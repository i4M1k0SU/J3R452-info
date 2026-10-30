# 開発環境

J3R452 (JCOP 4.5 / Java Card 3.0.5 Classic) 向けアプレット開発の環境。
カード本体の調査結果は [card-info.md](card-info.md) にある。

## 構成

| 役割 | ツール | 版 | 場所 |
|---|---|---|---|
| JDK | Eclipse Temurin など | 17 以上 | `tools/env.ps1` がシステムの JDK を自動検出 |
| ビルド | Apache Ant | 1.10.18 | `tools/apache-ant-1.10.18/` |
| CAP 生成 | ant-javacard | v26.05.15 | `tools/ant-javacard.jar` |
| Java Card SDK | Oracle JC SDK 3.2 v26.0 (`targetsdk=3.0.5` で 3.0.5 向けに変換) | jc320v26.0_kit | `tools/sdks/` |
| 拡張 API | JCOP Extended API | R1.3.4 | `extended/JCOPx_API-R1.3.4.jar` |
| カード管理 | GlobalPlatformPro | v25.10.20 (最新の安定版) | `tools/gp.jar` |
| ホスト側スクリプト | Python + pyscard + cryptography | 3.13.17 / 2.3.1 / 50.0.2 | `.venv/` |
| リーダ (接触) | SCM SCR3310 USB (PC/SC) | | |
| リーダ (非接触) | ACS ACR122U (PN532、PC/SC) | FW `ACR122U602` / ドライバ 4.2.8.0 | **制約あり** ([下記](#acr122u-非接触リーダ-の注意点)) |

補足:

- **JDK 17**
  - GlobalPlatformPro は JDK 17 以上が必要。
  - JC SDK v26.0 は JDK 8 以降で動く。
  - システムの `JAVA_HOME` (Corretto 11) は変更しない。`tools/env.ps1` がセッション内だけで切り替える。
- **Python 3.13**
  - pyscard には Python 3.14 用の Windows wheel がまだ無い。
  - そのため Python install manager で 3.13 を追加し、プロジェクト専用の venv を作った。
- `tools/sdks/jc305u4_kit` は 3.0.5 純正 SDK。参照用に置いている (ビルドでは使わない)。

## セットアップ (新しい PC で再現する場合)

```powershell
pwsh -File tools/setup.ps1
```

`setup.ps1` は次のものを取得する。何度実行しても不足分だけを取得する。

- Ant (SHA-512 を検証)
- ant-javacard / gp.jar (SHA-256 を検証)
- SDK (コミット固定の sparse checkout)
- Python venv

前提として、JDK 17 以上、git、Python install manager (`pymanager`) が必要。

## 日常の使い方

PowerShell で、セッションごとに最初に読み込む。

```powershell
. tools/env.ps1
```

`env.ps1` は次を設定する。

- `JAVA_HOME`、`ANT_HOME`、`PATH` (venv の python を含む)
- `gp` 関数 (`java -jar tools/gp.jar` のラッパ)

`gp` は PowerShell 組み込みの `Get-ItemProperty` エイリアスと名前が衝突する。そのため `env.ps1` は、このセッション内に限ってエイリアスを外す。

### アプレットのビルド・書き込み・テスト

```powershell
cd smoketest
ant build       # build/smoketest.cap を生成 (オフカード検証込み)
ant install     # カードへロード + インストール (既存は置き換え)
ant test        # 実機テスト (host/test_smoketest.py)
ant uninstall   # カードから削除
```

### よく使う gp コマンド

```powershell
gp --info                 # 認証なしでカード情報を表示 (CPLC、鍵情報、対応 SCP)
gp --list                 # 認証してインストール済みのアプレット/パッケージを一覧表示
gp --install x.cap        # インストール
gp --delete <AID>         # 削除
gp -d --list              # APDU トレースを付けて実行
```

鍵を指定しない場合、gp は既定鍵 `404142...4F` を使う。このカードはその鍵で認証できる (KVN=01, AES-128, SCP03)。

## ACR122U (非接触リーダ) の注意点

ACS ACR122U (PN532 搭載) で J3R452 を非接触で扱ったときに判明したこと。測定結果は [card-info.md](card-info.md#非接触インタフェース-iso-14443)。
手元の個体 (ファームウェア文字列 `ACR122U602`、USB REV 0207、ACS ドライバ 4.2.8.0) での結果であり、他の個体・ドライバでは異なる可能性がある。

### コマンド APDU は 54 バイトまで

- **CCID メッセージが 64 バイト (= USB フルスピードのバルクパケット 1 個) を超えると、`SCardTransmit` が `0x1F` (ERROR_GEN_FAILURE) で失敗する。**
  CCID ヘッダ 10 バイトを除くと、コマンド APDU (ヘッダ + Lc + データ + Le) は **54 バイト以下**でないと送れない。
  54 B は 6/6 回成功、55 B 以上は 0/6 回で、境界は固定である。
- RF を使わないリーダ内部コマンド (PN532 `ReadRegister`) でも同じ境界で失敗するため、カードや RF ではなくリーダの USB 転送側の問題である。
  別の USB ルートハブ (PC 直結) に挿し替えても変わらなかった。
- 失敗したあとはそのカード接続が壊れるので、`SCardDisconnect(UNPOWER)` → 再接続が必要。
- 応答 (カード → PC) 方向にはこの制約がない (210 バイトの `GET STATUS` 応答は通った)。
- 対策:
  - `gp` は **`--bs 48`** を付ける (LOAD ブロック 48 B → APDU 54 B)。既定の 255 B だと LOAD で失敗する。
    `gp --install x.cap --bs 48 -r "ACS ACR122 0"`
  - アプレットへ 48 B を超えるデータ (Ed25519 の長いメッセージ、X448 の公開鍵 56 B など) は送れない。
    smoketest の `ant test` は Ed25519 の 64 B メッセージで例外になる。全項目を通すには接触リーダを使う。

### ダミーカード `3B 00` で固まる個体がある

別の個体 (ファームウェア文字列 `ACR122U305`、USB REV 0305、USB デバイス名 "ACR122U for NFC") では、カードを載せてもドライバが ATR `3B 00` の擬似カードを報告し続け、
`FF CA` (UID 取得) や PICC 動作パラメータの取得/設定 (`FF 00 50`/`FF 00 51`) が `63 00` になった。
一方で PN532 直接コマンド (`FF 00 00 00 Lc D4 ...`) ではカードが正常に活性化できたので、カード側の問題ではない。
USB の抜き差しで復旧し、その後は `gp --info` / `--list` が通った。

### その他

- PC/SC のプロトコルは T=1 だけ (T=0 を指定すると接続できない)。
- ケース 4 コマンドには Le を付ける。付けないと応答データなしで `9000` だけが返ることがある (T=1 ではケース 3 として扱われる)。
- `gp` のリーダ指定は `-r "ACS ACR122 0"`。指定しない場合はカードが載っているリーダが自動で選ばれる。
- ドライバが報告する ATR は ACR122U が生成した擬似 ATR で、カードの ATS ではない。ATS は `FF CA 01 00 00` で取る。
- PN532 を直接叩く擬似 APDU `FF 00 00 00 Lc D4 ...` の応答は `61 xx` で返るので、`FF C0 00 00 xx` (GET RESPONSE) で取り出す。
  直接ポーリング (`InListPassiveTarget`) や `InRelease` を使ったあとはドライバ側の状態がずれるので、`SCardReconnect(RESET_CARD)` で再同期する。
- 出荷時の ISD 既定鍵でそのまま非接触からロード・インストールできる。非接触からの管理コマンドを制限する設定はされていない。

## 新しいプロダクトの作り方

1. `smoketest/` を `<名前>/` (リポジトリ直下) にコピーする。
2. `build.xml` の次の項目を書き換える。
   - `project name`
   - `package.aid` / `applet.aid`
   - `<cap package=...>` / `<applet class=...>`
3. ソースは `src/` に、ホスト側スクリプトは `host/` に置く。

`build.xml` は冒頭で `<import file="../tools/javacard.xml"/>` を読み込む。
これで次の共通設定が適用される。

- SDK パス、`targetsdk=3.0.5`
- JCOPx jar
- `gp` マクロ

JCOPx API を使う場合は、`<cap>` 内に `<import jar="${jcopx.jar}"/>` を入れる (smoketest と同じ)。

AID は `F04A3352343532` (`F0` + "J3R452") 以下を使っている。

- smoketest: パッケージ `...01`、アプレット `...0101`
- 新しいプロダクトでは `...02` 以降を使うと衝突しない。

## 注意事項

- **カード鍵の変更 (`gp --lock`) は、新しい鍵を確実に記録してから行うこと。**
  - 鍵を失うと、カードの管理ができなくなる。
  - 認証失敗を繰り返すと ISD がロックされる。
  - GlobalPlatformPro は、カードのクリプトグラムが合わない時点で中断する (EXTERNAL AUTHENTICATE を送らない)。
  - それでも、別の鍵で何度も試すことは避ける。
- `gp --lock-card` と ISD のライフサイクル変更 (SECURED への移行など) は元に戻せないことがある。開発中は OP_READY のままにすること。
- Ed25519 / X25519 / X448 のバイト順と署名形式は、RFC とは異なる。詳細は [card-info.md](card-info.md#ed25519--x25519--x448-の動作確認結果-重要) を参照。
- 非接触 (ISO 14443 Type A) でも SCP03 認証・ロード・インストール・アプレット実行ができる。ただし ACR122U には APDU 長の制約がある ([上記](#acr122u-非接触リーダ-の注意点))。
