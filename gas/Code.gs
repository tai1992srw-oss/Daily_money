/**
 * ダイエットログ API v2 (Google Apps Script)
 *
 * スプレッドシート「ダイエットログ」に紐付けて使う Web アプリ。
 * - GET  : 食事・活動・アドバイスの読み取り（today / range / summary）
 * - POST : 食事の追記(addMeal)、活動データのupsert(addActivity)、アドバイスの追記(addAdvice)
 *
 * 「1日」は午前5時に切り替わる（DAY_START_HOUR）。深夜2時の食事は前日扱い。
 * セットアップ手順は リポジトリの docs/diet-setup.md を参照。
 *
 * v1 からの更新時: このファイルを丸ごと貼り替え、TOKEN だけ自分の値に戻して
 * 「デプロイ → デプロイを管理 → 編集 → 新バージョン」で反映する（URLは変わらない）。
 * 活動ログのヘッダーは初回アクセス時に自動で新形式に更新される。
 */

// デプロイ前に必ずランダムな文字列に変更すること
const TOKEN = 'CHANGE_ME_TO_RANDOM_STRING';

const TZ = 'Asia/Tokyo';
const DAY_START_HOUR = 5; // 午前5時に日付が切り替わる

const MEAL_SHEET = '食事ログ';
const ACTIVITY_SHEET = '活動ログ';
const ADVICE_SHEET = 'アドバイス';

const ACTIVITY_HEADER = ['日付', '歩数', '総消費(kcal)', '活動消費(kcal)', '距離(km)', '睡眠(h)', '体重(kg)', '更新時刻'];
const ADVICE_HEADER = ['日付', '時刻', '種別', '内容'];

// ---------------------------------------------------------------- entrypoints

function doGet(e) {
  try {
    const p = (e && e.parameter) || {};
    if (p.token !== TOKEN) return json_({ ok: false, error: 'unauthorized' });

    const action = p.action || 'today';
    if (action === 'today') return json_(getDay_(p.date || todayStr_()));
    if (action === 'range') return json_(getRange_(p.from, p.to));
    if (action === 'summary') return json_(getSummaryDays_(Number(p.days || 7)));
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
    if (body.action === 'addAdvice') return json_(addAdvice_(body));
    return json_({ ok: false, error: 'unknown action: ' + body.action });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

// ------------------------------------------------------------------- actions

/**
 * 食事を1件追記する。
 * body: { date?, time?, meal, description, kcal, protein_g?, fat_g?, carbs_g?, note? }
 * date 省略時は「論理的な今日」（午前5時境界）。time 省略時は現在時刻。
 */
function addMeal_(body) {
  if (!body.description) return { ok: false, error: 'description is required' };
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
  mealSheet_().appendRow(row);
  return { ok: true, added: row };
}

/**
 * 活動データ（Health Connect 由来）を日付キーで upsert する。
 * body: { date?, steps?, total_kcal?, active_kcal?, distance_km?, sleep_h?, weight_kg? }
 * 未指定(null)のフィールドは既存の値を保持する（体重など日によって取れない項目のため）。
 */
function addActivity_(body) {
  const sheet = activitySheet_();
  const date = body.date || todayStr_();
  const updatedAt = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd HH:mm');

  const values = sheet.getDataRange().getValues();
  let existing = null;
  let rowIndex = -1;
  for (let i = 1; i < values.length; i++) {
    if (dateStr_(values[i][0]) === date) {
      existing = values[i];
      rowIndex = i + 1;
      break;
    }
  }

  const pick = function (incoming, colIndex) {
    if (incoming !== null && incoming !== undefined && incoming !== '') return Number(incoming);
    // 既存セルが日付書式に化けていた場合は引き継がず捨てる
    if (existing && !(existing[colIndex] instanceof Date)) return existing[colIndex];
    return '';
  };

  const row = [
    date,
    pick(body.steps, 1),
    pick(body.total_kcal, 2),
    pick(body.active_kcal, 3),
    pick(body.distance_km, 4),
    pick(body.sleep_h, 5),
    pick(body.weight_kg, 6),
    updatedAt,
  ];

  if (rowIndex <= 0) {
    rowIndex = sheet.getLastRow() + 1;
  }
  sheet.getRange(rowIndex, 1, 1, row.length).setValues([row]);
  // 歩数〜体重(B〜G列)を数値書式に固定する。Sheetsの自動判定でセルが
  // 日付/時刻書式になると、以後そのセルに書く数値がすべて日付に化けるため
  sheet.getRange(rowIndex, 2, 1, 6).setNumberFormat('0.###');
  return { ok: true, saved: row };
}

/**
 * アドバイス（Claudeの講評・提案）を追記する。
 * body: { date?, type?, content }  type 例: 日次レビュー / 週次レビュー / 提案
 */
function addAdvice_(body) {
  if (!body.content) return { ok: false, error: 'content is required' };
  const now = new Date();
  const row = [
    body.date || todayStr_(),
    Utilities.formatDate(now, TZ, 'HH:mm'),
    body.type || 'アドバイス',
    body.content,
  ];
  adviceSheet_().appendRow(row);
  return { ok: true, added: row };
}

/** 指定日の食事一覧・合計・活動データ・アドバイスを返す。 */
function getDay_(date) {
  const db = loadAll_();
  return dayFromDb_(db, date);
}

/** from〜to（両端含む）の日別サマリーを返す。カレンダー表示用。最大62日。 */
function getRange_(from, to) {
  if (!from || !to) return { ok: false, error: 'from and to are required (yyyy-MM-dd)' };
  const start = parseDate_(from);
  const end = parseDate_(to);
  if (!start || !end) return { ok: false, error: 'invalid date format' };
  const count = Math.round((end.getTime() - start.getTime()) / 86400000) + 1;
  if (count < 1 || count > 62) return { ok: false, error: 'range must be 1-62 days' };

  const db = loadAll_();
  const days = [];
  for (let i = 0; i < count; i++) {
    const date = Utilities.formatDate(new Date(start.getTime() + i * 86400000), TZ, 'yyyy-MM-dd');
    days.push(daySummaryFromDb_(db, date));
  }
  return { ok: true, from: from, to: to, days: days };
}

/** 直近 N 日の日別サマリー（今日を含む、新しい順）。レビュー用。 */
function getSummaryDays_(days) {
  const db = loadAll_();
  const result = [];
  const todayMs = parseDate_(todayStr_()).getTime();
  for (let d = 0; d < days; d++) {
    const date = Utilities.formatDate(new Date(todayMs - d * 86400000), TZ, 'yyyy-MM-dd');
    result.push(daySummaryFromDb_(db, date));
  }
  return { ok: true, days: result };
}

// -------------------------------------------------------------- data helpers

/** 3シートを一度だけ読み、日付でひけるインデックスを作る。 */
function loadAll_() {
  const meals = {};
  const mealValues = mealSheet_().getDataRange().getValues();
  for (let i = 1; i < mealValues.length; i++) {
    const r = mealValues[i];
    const date = dateStr_(r[0]);
    if (!date) continue;
    if (!meals[date]) meals[date] = [];
    meals[date].push({
      time: timeStr_(r[1]),
      meal: String(r[2] || ''),
      description: String(r[3] || ''),
      kcal: num_(r[4]),
      protein_g: num_(r[5]),
      fat_g: num_(r[6]),
      carbs_g: num_(r[7]),
      note: String(r[8] || ''),
    });
  }

  const activity = {};
  const actValues = activitySheet_().getDataRange().getValues();
  for (let i = 1; i < actValues.length; i++) {
    const r = actValues[i];
    const date = dateStr_(r[0]);
    if (!date) continue;
    activity[date] = {
      steps: numOrNull_(r[1]),
      total_kcal: numOrNull_(r[2]),
      active_kcal: numOrNull_(r[3]),
      distance_km: numOrNull_(r[4]),
      sleep_h: numOrNull_(r[5]),
      weight_kg: numOrNull_(r[6]),
    };
  }

  const advice = {};
  const advValues = adviceSheet_().getDataRange().getValues();
  for (let i = 1; i < advValues.length; i++) {
    const r = advValues[i];
    const date = dateStr_(r[0]);
    if (!date) continue;
    if (!advice[date]) advice[date] = [];
    advice[date].push({
      time: timeStr_(r[1]),
      type: String(r[2] || ''),
      content: String(r[3] || ''),
    });
  }

  return { meals: meals, activity: activity, advice: advice };
}

function dayFromDb_(db, date) {
  const meals = db.meals[date] || [];
  const totals = { kcal: 0, protein_g: 0, fat_g: 0, carbs_g: 0 };
  meals.forEach(function (m) {
    totals.kcal += m.kcal;
    totals.protein_g += m.protein_g;
    totals.fat_g += m.fat_g;
    totals.carbs_g += m.carbs_g;
  });
  return {
    ok: true,
    date: date,
    meals: meals,
    totals: totals,
    activity: db.activity[date] || null,
    advice: db.advice[date] || [],
  };
}

function daySummaryFromDb_(db, date) {
  const day = dayFromDb_(db, date);
  const act = day.activity;
  const burned = act ? act.total_kcal : null;
  return {
    date: date,
    intake_kcal: day.totals.kcal,
    burned_kcal: burned,
    balance_kcal: burned === null ? null : day.totals.kcal - burned,
    steps: act ? act.steps : null,
    weight_kg: act ? act.weight_kg : null,
    sleep_h: act ? act.sleep_h : null,
    meals: day.meals.length,
    advice_count: day.advice.length,
  };
}

// ------------------------------------------------------------- sheet helpers

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
    sheet.appendRow(ACTIVITY_HEADER);
    return sheet;
  }
  // v1 (5列) からのヘッダー移行
  if (String(sheet.getRange(1, 5).getValue()) !== ACTIVITY_HEADER[4]) {
    // 旧「更新時刻」(5列目) の値を8列目へ移す
    const values = sheet.getDataRange().getValues();
    for (let i = 1; i < values.length; i++) {
      const old = values[i][4];
      if (old !== '' && values[i].length <= 5) {
        sheet.getRange(i + 1, 8).setValue(old);
        sheet.getRange(i + 1, 5).setValue('');
      }
    }
    sheet.getRange(1, 1, 1, ACTIVITY_HEADER.length).setValues([ACTIVITY_HEADER]);
  }
  return sheet;
}

function adviceSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(ADVICE_SHEET);
  if (!sheet) {
    sheet = ss.insertSheet(ADVICE_SHEET);
    sheet.appendRow(ADVICE_HEADER);
  }
  return sheet;
}

// ------------------------------------------------------------- value helpers

/** 午前5時境界での「今日」の日付文字列。 */
function todayStr_() {
  const shifted = new Date(Date.now() - DAY_START_HOUR * 3600 * 1000);
  return Utilities.formatDate(shifted, TZ, 'yyyy-MM-dd');
}

function parseDate_(s) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(s || '').trim());
  if (!m) return null;
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 12, 0, 0);
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

function numOrNull_(v) {
  if (v === '' || v === null || v === undefined) return null;
  // セルが日付/時刻書式に化けていると Date が返る（例: 距離0.07が "1899-12-30 1:40" になる）。
  // 数値列に Date が入っていたら壊れた値として捨てる
  if (v instanceof Date) return null;
  const n = Number(v);
  return isFinite(n) ? n : null;
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(
    ContentService.MimeType.JSON
  );
}
