# ダイエットログ

Claude と連携するダイエット管理 Android アプリ。
食事はチャットで Claude に伝えて記録し、活動データは Pixel Watch (Health Connect) から取得。
データはすべて Google スプレッドシートに集約され、アプリは「今日のカロリー収支」と「カレンダー」を表示する。

```
Pixel Watch ─▶ Fitbit ─▶ Health Connect ─▶ アプリ ─┐（活動データを書き込み）
                                                    ▼
Claude チャット（食事・アドバイス）──▶ GAS Web API ──▶ スプレッドシート「ダイエットログ」
                                                    │
アプリ ◀── 食事ログ・収支・アドバイスを取得 ◀──────┘
```

※ 元の家計簿アプリ (Daily Budget) は `main` ブランチにあります。本ブランチで別アプリ
（applicationId: `com.dietlog`）として独立しました。

## 主な機能

- **今日タブ**: カロリー収支（摂取−ウォッチ消費）、カロリー3ライン（目標/ゆるく減量/キープ）色分けとタンパク質3段階（最低/目標/上限）の進捗、PFC、
  距離・睡眠・体重、体重グラフ（直近30日）、Claude からのアドバイス、食事一覧。下スワイプで更新
- **カレンダータブ**: 月表示で日々の収支を色分け表示（🟢収支マイナス/🔴プラス/💡アドバイスあり）、
  日タップで詳細（食事・活動・アドバイス）
- **お店タブ**: 行った店を都道府県ごとにまとめて表示。エリア順/よく行く順/最近行った順/うまい順で
  並べ替え、都道府県で絞り込み。訪問回数・合計カロリー・平均★・最終訪問日つき。
  店をタップすると詳細画面：その店で食べた全記録（日付ごと・写真・メニュー・メモ）と地図リンク
- **食事記録**: Claude チャット / Cowork の `/meal-log` スキルで記録（カロリー・PFC自動推定）
- **写真・店の記録**: 食事の写真を貼ると Drive に保存してカードにサムネイル表示（タップで拡大）、
  Google マップ / 食べログの URL、または店名だけを送ると店を解決して記録
  （チップをタップでその店を開く）
- **感想・★評価**: 食事の感想を送るとメモとは別の「感想」列に記録され、食事カードに 💬 と ★ で表示。
  星はお店の平均評価と「うまい順」ソートに反映
- **レビュー**: `/diet-review` スキルで講評を生成し、アドバイスシートに保存 → アプリで見返せる
- **日付境界は午前5時**: 深夜0〜5時の食事は前日としてカウント（GAS・アプリ共通）
- **Health Connect**: 歩数・総消費/活動消費カロリー・距離・睡眠・体重を取得しシートへ書き戻し

## 技術スタック

- Kotlin / Jetpack Compose / Material3
- MVVM (ViewModel + StateFlow) / Hilt / Coroutines
- DataStore（設定保存）
- Health Connect Client
- バックエンド: Google Apps Script (`gas/Code.gs`) + Google スプレッドシート
- 最小SDK: Android 8.0 (API 26)

## プロジェクト構造

```
app/src/main/java/com/dietlog/
├── data/
│   ├── diet/             # モデル、GAS APIクライアント、Health Connect、設定ストア
│   └── repository/       # DietRepository（同期ロジック）
├── ui/
│   ├── components/       # 食事/アドバイスカード、設定ダイアログ
│   ├── screens/          # 今日 (Diet)、カレンダー (Calendar)、お店 (Places)
│   └── theme/
├── DietLogApplication.kt
└── MainActivity.kt
gas/Code.gs               # スプレッドシート側 Web API（デプロイして使う）
.claude/skills/meal-log/  # 食事記録スキル（写真アップロード・店名解決スクリプト同梱）
.claude/skills/diet-review/ # レビュー・アドバイス記録スキル
docs/diet-setup.md        # セットアップ手順
```

## セットアップ / ビルド

[docs/diet-setup.md](docs/diet-setup.md) を参照。ビルドは Android Studio または `./gradlew assembleDebug`。

## ライセンス

このプロジェクトは個人使用を目的としています。
