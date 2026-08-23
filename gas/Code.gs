/**
 * ダイエットログ API (Google Apps Script)
 *
 * スプレッドシート「ダイエットログ」に紐付けて使う Web アプリ。
 * - GET  : 食事ログ・活動ログの読み取り（Android アプリが使用）
 * - POST : 食事の追記（Claude チャット/スキルが使用）、活動データの書き込み（アプリが使用）
 *
 * セットアップ手順は リポジトリの docs/diet-setup.md を参照。
 */

// デプロイ前に必ずランダムな文字列に変更すること（例: パスワード生成器で32文字）
const TOKEN = 'CHANGE_ME_TO_RANDOM_STRING';

const TZ = 'Asia/Tokyo';
const MEAL_SHEET = '食事ログ';
const ACTIVITY_SHEET = '活動ログ';

// ---------------------------------------------------------------- entrypoints

function doGet(e) {
  try {
    const p = (e && e.parameter) || {};
    if (p.token !== TOKEN) return json_({ ok: false, error: 'unauthorized' });

    const action = p.action || 'today';
    if (action === 'today') return json_(getDay_(p.date || todayStr_()));
    if (action === 'summary') return json_(getSummary_(Number(p.days || 7)));
    return json_({ ok: false, error: 'unknown action: ' + action });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function doPost(e) {
  try {
    const body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    if (body.token !== TOKEN) return json_({ ok: false, error: 'unauthorized' });

    if (body.action === 'addMeal') return json_(addMeal_(body));
    if (body.action === 'addActivity') return json_(addActivity_(body));
    return json_({ ok: false, error: 'unknown action: ' + body.action });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

// ------------------------------------------------------------------- actions

/**
 * 食事を1件追記する。
 * body: { date?, time?, meal, description, kcal, protein_g?, fat_g?, carbs_g?, note? }
 * date/time 省略時は現在時刻(JST)。meal は 朝食/昼食/夕食/間食 など自由。
 */
function addMeal_(body) {
  if (!body.description) return { ok: false, error: 'description is required' };
  const sheet = mealSheet_();
  const now = new Date();
  const row = [
    body.date || todayStr_(),
    body.time || Utilities.formatDate(now, TZ, 'HH:mm'),
    body.meal || '',
    body.description,
    num_(body.kcal),
    num_(body.protein_g),
    num_(body.fat_g),
    num_(body.carbs_g),
    body.note || '',
  ];
  sheet.appendRow(row);
  return { ok: true, added: row };
}

/**
 * 活動データ（Health Connect 由来）を日付キーで upsert する。
 * body: { date?, steps, total_kcal, active_kcal }
 */
function addActivity_(body) {
  const sheet = activitySheet_();
  const date = body.date || todayStr_();
  const updatedAt = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd HH:mm');
  const row = [date, num_(body.steps), num_(body.total_kcal), num_(body.active_kcal), updatedAt];

  const values = sheet.getDataRange().getValues();
  for (let i = 1; i < values.length; i++) {
    if (dateStr_(values[i][0]) === date) {
      sheet.getRange(i + 1, 1, 1, row.length).setValues([row]);
      return { ok: true, updated: row };
    }
  }
  sheet.appendRow(row);
  return { ok: true, added: row };
}

/** 指定日の食事一覧・合計・活動データを返す。 */
function getDay_(date) {
  const meals = [];
  const totals = { kcal: 0, protein_g: 0, fat_g: 0, carbs_g: 0 };

  const values = mealSheet_().getDataRange().getValues();
  for (let i = 1; i < values.length; i++) {
    const r = values[i];
    if (dateStr_(r[0]) !== date) continue;
    const meal = {
      time: timeStr_(r[1]),
      meal: String(r[2] || ''),
      description: String(r[3] || ''),
      kcal: num_(r[4]),
      protein_g: num_(r[5]),
      fat_g: num_(r[6]),
      carbs_g: num_(r[7]),
      note: String(r[8] || ''),
    };
    meals.push(meal);
    totals.kcal += meal.kcal;
    totals.protein_g += meal.protein_g;
    totals.fat_g += meal.fat_g;
    totals.carbs_g += meal.carbs_g;
  }

  return { ok: true, date: date, meals: meals, totals: totals, activity: getActivity_(date) };
}

/** 直近 N 日の日別サマリー（摂取・消費・収支）を返す。 */
function getSummary_(days) {
  const result = [];
  const now = new Date();
  for (let d = 0; d < days; d++) {
    const date = Utilities.formatDate(new Date(now.getTime() - d * 86400000), TZ, 'yyyy-MM-dd');
    const day = getDay_(date);
    const burned = day.activity ? day.activity.total_kcal : null;
    result.push({
      date: date,
      intake_kcal: day.totals.kcal,
      burned_kcal: burned,
      balance_kcal: burned === null ? null : day.totals.kcal - burned,
      steps: day.activity ? day.activity.steps : null,
      meals: day.meals.length,
    });
  }
  return { ok: true, days: result };
}

function getActivity_(date) {
  const values = activitySheet_().getDataRange().getValues();
  for (let i = 1; i < values.length; i++) {
    if (dateStr_(values[i][0]) === date) {
      return {
        steps: num_(values[i][1]),
        total_kcal: num_(values[i][2]),
        active_kcal: num_(values[i][3]),
      };
    }
  }
  return null;
}

// ------------------------------------------------------------------- helpers

function mealSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(MEAL_SHEET);
  if (!sheet) {
    // CSV から変換した初期タブ（ヘッダー行あり）を食事ログとして使う
    sheet = ss.getSheets()[0];
    sheet.setName(MEAL_SHEET);
  }
  return sheet;
}

function activitySheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(ACTIVITY_SHEET);
  if (!sheet) {
    sheet = ss.insertSheet(ACTIVITY_SHEET);
    sheet.appendRow(['日付', '歩数', '総消費(kcal)', '活動消費(kcal)', '更新時刻']);
  }
  return sheet;
}

function todayStr_() {
  return Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
}

/** セル値（Date か文字列）を 'yyyy-MM-dd' に正規化する。 */
function dateStr_(v) {
  if (v instanceof Date) return Utilities.formatDate(v, TZ, 'yyyy-MM-dd');
  return String(v || '').trim();
}

/** セル値（Date か文字列）を 'HH:mm' に正規化する。 */
function timeStr_(v) {
  if (v instanceof Date) return Utilities.formatDate(v, TZ, 'HH:mm');
  return String(v || '').trim();
}

function num_(v) {
  const n = Number(v);
  return isFinite(n) ? n : 0;
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(
    ContentService.MimeType.JSON
  );
}
