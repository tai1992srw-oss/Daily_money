# Daily Budget App

今日使える予算を一目で確認し、サッと記録できるシンプルな家計簿アプリ

## 技術スタック

- **言語**: Kotlin
- **UIフレームワーク**: Jetpack Compose
- **アーキテクチャ**: MVVM (ViewModel + StateFlow)
- **データベース**: Room (SQLite)
- **DI**: Hilt
- **非同期処理**: Coroutines + Flow
- **最小SDK**: Android 8.0 (API 26)

## プロジェクト構造

```
app/src/main/java/com/dailybudget/
├── data/
│   ├── database/         # Room entities, DAOs, Database
│   ├── model/            # Data models (Category, TransactionType)
│   └── repository/       # Repository layer
├── di/                   # Hilt dependency injection modules
├── ui/
│   ├── components/       # Reusable UI components
│   ├── screens/          # Screen composables and ViewModels
│   └── theme/            # App theme, colors, typography
├── DailyBudgetApplication.kt
└── MainActivity.kt
```

## 主な機能

### フェーズ1 (現在実装済み)

- ✅ メイン画面で今日の予算を大きく表示
- ✅ 支出・収入の入力機能
- ✅ カテゴリー選択（食費、交通費、日用品、娯楽、その他）
- ✅ 今日の取引履歴表示
- ✅ 日付変更時の自動繰越処理
- ✅ 初回起動時の初期設定ダイアログ
- ✅ データのローカル保存（Room Database）

### 予算計算ロジック

```
今日使える予算 = 初期予算 + 前日の残高 + 今日の収入 - 今日の支出
```

## データベース設計

### Transaction (取引テーブル)
- 日付、種別（支出/収入）、金額、メモ、カテゴリー、タイムスタンプ

### Settings (設定テーブル)
- 1日あたりの予算、最終更新日

### DailyBalance (日次残高テーブル)
- 日付、残高、繰越額

## ビルド方法

1. Android Studio で プロジェクトを開く
2. Gradle sync を実行
3. エミュレータまたは実機で実行

## 今後の予定（フェーズ2）

- カテゴリーのカスタマイズ
- 月次・週次のレポート機能
- グラフ表示
- データバックアップ/復元
- 複数予算管理
- ホーム画面ウィジェット
- テーマカラー変更

## ライセンス

このプロジェクトは個人使用を目的としています。
