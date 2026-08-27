---
name: meal-log
description: 食べたものを伝えるとカロリー・PFCを推定してスプレッドシート「ダイエットログ」に記録する。「/meal-log 唐揚げ定食」のように食事内容を渡す。食事の写真を貼られたとき、Google マップや食べログの店URLを送られたときも、写真・店名として同じ記録に残す。食事の記録・カロリー計算を頼まれたときに使う。
---

# 食事記録スキル

ユーザーが食べたものをカロリー推定し、GAS Web API 経由でスプレッドシート「ダイエットログ」に追記する。

## 設定の読み込み

まず `.claude/diet-bot.local.json`（リポジトリルート直下、git管理外）を読む:

```json
{ "api_url": "https://script.google.com/macros/s/xxxx/exec", "token": "..." }
```

ファイルがない場合はユーザーに API URL とトークンを尋ね（セットアップ未了なら docs/diet-setup.md を案内）、答えをこのファイルに保存してから続行する。

## 手順

0. **写真・店URLの有無を確認**: ユーザーが画像を貼っていれば「写真あり」、Google マップ /
   食べログ / その他の店の URL を含んでいれば「店あり」として、下の「写真を記録する」
   「店を記録する」を先に実行し、得られた `photo_url` / `place_name` / `place_url` を
   手順3の addMeal に含める。**食事の記録より先に写真・店を解決すること**（後から
   updateMeal で足すより1回のPOSTで済む）。
1. **食事内容の解釈**: 引数（または直前のユーザー発言）から食事内容を把握する。
   - 区分（朝食/昼食/夕食/間食）は発言や現在時刻(JST)から推定する（5〜10時:朝食、10〜15時:昼食、15〜18時:間食、18時〜:夕食、0〜5時:夜食→夕食扱い）
   - **日付境界は午前5時**。深夜0〜5時の食事は自動的に「前日」として記録される（GAS側で処理されるので date は渡さなくてよい）
   - 「昨日の夜」など明示的に過去の食事のときだけ date/time を指定する（5時境界で考える。例: 今朝3時のラーメンは「昨日」扱いなので指定不要）
2. **カロリー・PFC推定**: 一般的な栄養データに基づき、合計の kcal・タンパク質・脂質・炭水化物(g)を推定する。量が不明なら標準的な1人前と仮定し、推定の根拠を一言添える。複数品はまとめて1レコードでよい（内容欄に列挙）。
3. **記録**: 以下のように POST する。**ボディは必ず UTF-8 のファイルに書き出して `--data-binary @` で送る**:

```bash
BODY=<scratchpad>/meal.json
cat > "$BODY" <<'EOF'
{"token":"<token>","action":"addMeal","meal":"昼食","description":"唐揚げ定食とビール1杯","kcal":1050,"protein_g":38,"fat_g":45,"carbs_g":95,"note":"ビール中ジョッキ含む","photo_url":"","place_name":"","place_url":""}
EOF
curl -s -L --data-binary @"$BODY" \
  -H 'Content-Type: application/json; charset=utf-8' \
  '<api_url>'
```

   - **`-X POST` は付けないこと。** GAS は 302 を返すため、`-X POST` だとリダイレクト先へも
     POST が強制され `411 Length Required` / `405` になる。`-X` 無しなら curl が自動で GET に切り替わり、
     正常に JSON を受け取れる（GAS の処理は最初の `/exec` への POST 時点で完了）。
   - **エラーが返っても行は既に書き込まれていることがある。** リトライ前に必ず `action=today` で重複を確認する。
   - インラインの `-d '{...}'` は使わない（Windows では日本語が CP932 で渡り文字化けする）。

   date/time は省略すると現在時刻(JST)になる。過去の食事のみ `"date":"YYYY-MM-DD","time":"HH:mm"` を付ける。
4. **今日の合計を確認**: `curl -sL '<api_url>?token=<token>&action=today'` で今日の合計を取得する。
5. **報告**: 記録した内容（推定kcal・PFC）と、今日ここまでの合計摂取カロリーを簡潔に伝える。activity データがあれば消費カロリーとの収支も添える。写真・店を記録したときは一言添える（「写真も保存した」「〇〇として記録した」）。

## 写真を記録する

ユーザーがチャットに食事の写真を貼ったら、Drive に保存して URL を食事ログに残す。
**写真の中身は必ずカロリー推定にも使う**（何がどれくらい写っているかを見て量を見積もる）。

```bash
python .claude/skills/meal-log/photo_upload.py            # 会話に貼られた最新の画像
python .claude/skills/meal-log/photo_upload.py <画像パス>  # ファイルで渡されたとき
```

- `{"ok":true,"url":"https://drive.google.com/thumbnail?id=...&sz=w1600","id":"..."}` が返るので、
  この `url` を addMeal の `photo_url` に入れる。アプリの食事カードにサムネイルが出る
- 貼られた画像は会話ログ（`~/.claude/projects/.../*.jsonl`）から取り出している。スクリプトが
  「画像が見つかりません」と言うときは、ユーザーに画像ファイルのパスを聞いて引数で渡す
- 保存先は マイドライブの「ダイエットログ写真」フォルダ。アプリから見えるようリンク共有(閲覧)にしている
- **食事を記録した後で写真だけ来た**場合は updateMeal で後付けする:
  `{"token":"<token>","action":"updateMeal","photo_url":"<url>"}` （その日の最後の食事行が対象。
  別の行に付けるなら `"time":"12:30"` か `"row":15` を足す）

## 店を記録する

Google マップ（`maps.app.goo.gl` / `google.com/maps`）や食べログ（`tabelog.com`）の URL が
発言に含まれていたら、店名を解決して記録する。

```bash
python .claude/skills/meal-log/place_info.py '<URL>'
```

- `{"ok":true,"name":"店名","url":"..."}` の `name` を `place_name`、`url` を `place_url` に入れる
- `name` が空、または明らかに店名でない（「Google マップ」等）ときはユーザーに店名を聞く。
  ユーザーが発言中に店名を書いていればそれを優先する
- URL が無くて店名だけ言われたときは `place_name` だけ入れてよい（`place_url` は空）
- 食事の記録後に URL だけ送られてきたら updateMeal で後付けする:
  `{"token":"<token>","action":"updateMeal","place_name":"〇〇","place_url":"<url>"}`
- アプリでは食事カードに店名チップが出て、タップでその URL を開く

## 注意

- レスポンスを Python で読むときは **UTF-8 を明示**する。Windows の `sys.stdin.encoding` は cp932 なので
  `curl ... | python -c "json.load(sys.stdin)"` は日本語を壊す。`curl -o file` してから `io.open(file, encoding='utf-8')` で読む
- 日本語を含む Python スクリプトを `python - <<EOF` (stdin) で渡すと cp932 で解釈されリテラルが壊れる。
  **.py ファイルに書き出してから実行**すること
- Git Bash のコンソールは日本語を表示できず必ず化ける。**端末の表示化けと実データの破損を混同しない**。
  中身は `.encode('unicode_escape')` して ASCII で確認する
- `{"ok":false,"error":"unauthorized"}` はトークン不一致。設定ファイルを確認してもらう
- 推定はあくまで概算であることを最初の1回だけ断る（毎回は言わない）
- ユーザーが「まとめ」「振り返り」「レビュー」を求めたら meal-log ではなく **diet-review スキル**を使う（講評をアドバイスシートに記録するため）
