/**
 * ダイエットログ API v2 (Google Apps Script)
 *
 * スプレッドシート「ダイエットログ」に紐付けて使う Web アプリ。
 * - GET  : 食事・活動・アドバイスの読み取り（today / range / summary）
 * - POST : 食事の追記(addMeal)、活動データのupsert(addActivity)、アドバイスの追記(addAdvice)、
 *          写真の保存(uploadPhoto)、既存の食事への写真・店の後付け(updateMeal)
 *
 * 「1日」は午前5時に切り替わる（DAY_START_HOUR）。深夜2時の食事は前日扱い。
 * セットアップ手順は リポジトリの docs/diet-setup.md を参照。
 *
 * v1 からの更新時: このファイルを丸ごと貼り替え、TOKEN だけ自分の値に戻して
 * 「デプロイ → デプロイを管理 → 編集 → 新バージョン」で反映する（URLは変わらない）。
 * 活動ログ・食事ログのヘッダーは初回アクセス時に自動で新形式に更新される。
 *
 * 写真は マイドライブの「ダイエットログ写真」フォルダに保存し、アプリから見えるよう
 * 「リンクを知っている全員が閲覧可」にする（URLを知られなければ他人には見えない）。
 */

// デプロイ前に必ずランダムな文字列に変更すること
const TOKEN = 'CHANGE_ME_TO_RANDOM_STRING';

const TZ = 'Asia/Tokyo';
const DAY_START_HOUR = 5; // 午前5時に日付が切り替わる

const MEAL_SHEET = '食事ログ';
const ACTIVITY_SHEET = '活動ログ';
const ADVICE_SHEET = 'アドバイス';

const ACTIVITY_HEADER = ['日付', '歩数', '総消費(kcal)', '活動消費(kcal)', '距離(km)', '睡眠(h)', '体重(kg)', '更新時刻'];
// 食事ログの J〜L 列（A〜I は既存のまま。無ければ初回アクセス時に見出しだけ足す）
// 写真URLは複数枚をカンマ区切りで持つ（1枚だけの旧データもそのまま読める）
const MEAL_EXTRA_HEADER = ['写真URL', '店名', '店URL', '店エリア'];
const MEAL_EXTRA_COL = 10; // J列
const PHOTO_FOLDER_NAME = 'ダイエットログ写真';
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
    if (action === 'places') return json_(getPlaces_());
    if (action === 'placeMeals') return json_(getPlaceMeals_(p.name || ''));
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
    if (body.action === 'uploadPhoto') return json_(uploadPhoto_(body));
    if (body.action === 'updateMeal') return json_(updateMeal_(body));
    return json_({ ok: false, error: 'unknown action: ' + body.action });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

// ------------------------------------------------------------------- actions

/**
 * 食事を1件追記する。
 * body: { date?, time?, meal, description, kcal, protein_g?, fat_g?, carbs_g?, note?,
 *          photo_url?, place_name?, place_url?, place_area? }
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
    body.photo_url || '',
    body.place_name || '',
    body.place_url || '',
    body.place_area || '',
  ];
  const sheet = mealSheet_();
  sheet.appendRow(row);
  return { ok: true, added: row, row: sheet.getLastRow() };
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

/**
 * 食事の写真を Drive に保存し、アプリから表示できる URL を返す。
 * body: { data(base64), mime?, filename? }
 * 保存先は マイドライブ直下の「ダイエットログ写真」フォルダ。
 * 画像はアプリ（未ログイン）から読めるようリンク共有(閲覧)にする。
 */
function uploadPhoto_(body) {
  if (!body.data) return { ok: false, error: 'data (base64) is required' };
  const mime = body.mime || 'image/jpeg';
  const ext = mime.indexOf('png') >= 0 ? 'png' : mime.indexOf('webp') >= 0 ? 'webp' : 'jpg';
  const stamp = Utilities.formatDate(new Date(), TZ, 'yyyyMMdd_HHmmss');
  const base = String(body.filename || '').replace(/\.[A-Za-z0-9]+$/, '').replace(/[\/:*?"<>|]/g, '_');
  const name = (base ? stamp + '_' + base : stamp) + '.' + ext;

  const blob = Utilities.newBlob(Utilities.base64Decode(body.data), mime, name);
  const file = photoFolder_().createFile(blob);
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  const id = file.getId();
  return {
    ok: true,
    id: id,
    // thumbnail エンドポイントは認証なしで画像バイトを返すのでアプリ表示向き
    url: 'https://drive.google.com/thumbnail?id=' + id + '&sz=w1600',
    view_url: 'https://drive.google.com/file/d/' + id + '/view',
  };
}

/**
 * 既存の食事行に写真・店の情報を後付けする（「さっきの店これ」用）。
 * body: { date?, time?, row?, photo_url?, photo_append?, place_name?, place_url?, place_area?, note? }
 * row 指定が最優先。無ければ date（既定は今日）の中で time 一致、time も無ければ最終行。
 * photo_append: true なら既存の写真を残して追記する（写真を2枚目以降として足すとき）。
 */
function updateMeal_(body) {
  const sheet = mealSheet_();
  const values = sheet.getDataRange().getValues();
  let rowIndex = Number(body.row || 0);

  if (!rowIndex) {
    const date = body.date || todayStr_();
    const time = body.time ? timeStr_(body.time) : '';
    for (let i = 1; i < values.length; i++) {
      if (dateStr_(values[i][0]) !== date) continue;
      if (time && timeStr_(values[i][1]) !== time) continue;
      rowIndex = i + 1;
    }
    if (!rowIndex) return { ok: false, error: 'meal not found for ' + date + (time ? ' ' + time : '') };
  }
  if (rowIndex < 2 || rowIndex > sheet.getLastRow()) {
    return { ok: false, error: 'invalid row: ' + rowIndex };
  }

  const setIf = function (value, col) {
    if (value === null || value === undefined || value === '') return;
    sheet.getRange(rowIndex, col).setValue(value);
  };
  setIf(body.note, 9);
  if (body.photo_url && body.photo_append) {
    setIf(appendPhoto_(values[rowIndex - 1][MEAL_EXTRA_COL - 1], body.photo_url), MEAL_EXTRA_COL);
  } else {
    setIf(body.photo_url, MEAL_EXTRA_COL);
  }
  setIf(body.place_name, MEAL_EXTRA_COL + 1);
  setIf(body.place_url, MEAL_EXTRA_COL + 2);
  setIf(body.place_area, MEAL_EXTRA_COL + 3);

  const saved = sheet.getRange(rowIndex, 1, 1, MEAL_EXTRA_COL + 3).getValues()[0];
  return { ok: true, row: rowIndex, saved: saved };
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

/**
 * 記録に店名が入っている食事をまとめて「行った店」の一覧を返す（アプリのお店タブ用）。
 * 同じ店の同じ日の複数行は1回の訪問として数える。
 */
function getPlaces_() {
  const values = mealSheet_().getDataRange().getValues();
  const byKey = {};

  for (let i = 1; i < values.length; i++) {
    const r = values[i];
    const name = String(r[10] || '').trim();
    const url = String(r[11] || '').trim();
    if (!name && !url) continue;

    const key = name || url;
    if (!byKey[key]) {
      byKey[key] = {
        name: name, url: url, area: String(r[12] || '').trim(),
        visits: 0, meals: 0, total_kcal: 0, first_date: '', last_date: '', dates: {},
      };
    }
    const e = byKey[key];
    // 後の行で埋まった URL・エリアも拾う（先に店名だけ記録した場合のため）
    if (!e.url && url) e.url = url;
    if (!e.area && r[12]) e.area = String(r[12]).trim();
    e.meals += 1;
    e.total_kcal += num_(r[4]);

    const date = dateStr_(r[0]);
    if (date) {
      e.dates[date] = true;
      if (!e.first_date || date < e.first_date) e.first_date = date;
      if (date > e.last_date) e.last_date = date;
    }
  }

  const places = [];
  Object.keys(byKey).forEach(function (key) {
    const e = byKey[key];
    e.visits = Object.keys(e.dates).length;
    delete e.dates;
    places.push(e);
  });
  // 新しく行った順。同着はよく行く順
  places.sort(function (a, b) {
    if (a.last_date !== b.last_date) return a.last_date < b.last_date ? 1 : -1;
    return b.visits - a.visits;
  });
  return { ok: true, places: places };
}

/**
 * 指定した店で食べた記録の一覧（お店タブの詳細画面用）。日付の新しい順に返す。
 * 店名の一致で拾う。店名が空で URL だけ記録された行は URL 一致でも拾う。
 */
function getPlaceMeals_(name) {
  if (!name) return { ok: false, error: 'name is required' };
  const values = mealSheet_().getDataRange().getValues();
  const meals = [];
  for (let i = 1; i < values.length; i++) {
    const r = values[i];
    const rowName = String(r[10] || '').trim();
    const rowUrl = String(r[11] || '').trim();
    if (rowName !== name && rowUrl !== name) continue;
    meals.push({
      date: dateStr_(r[0]),
      time: timeStr_(r[1]),
      meal: String(r[2] || ''),
      description: String(r[3] || ''),
      kcal: num_(r[4]),
      protein_g: num_(r[5]),
      fat_g: num_(r[6]),
      carbs_g: num_(r[7]),
      note: String(r[8] || ''),
      photos: photoList_(r[9]),
      place_name: rowName,
      place_url: rowUrl,
      place_area: String(r[12] || ''),
    });
  }
  meals.sort(function (a, b) {
    if (a.date !== b.date) return a.date < b.date ? 1 : -1;
    return a.time < b.time ? -1 : 1;
  });
  return { ok: true, name: name, meals: meals };
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
      photo_url: String(r[9] || ''),
      photos: photoList_(r[9]),
      place_name: String(r[10] || ''),
      place_url: String(r[11] || ''),
      place_area: String(r[12] || ''),
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
  // v2 (9列) からの見出し移行。写真・店の列が無ければ足す
  const extras = sheet.getRange(1, MEAL_EXTRA_COL, 1, MEAL_EXTRA_HEADER.length).getValues()[0];
  if (String(extras[0]) !== MEAL_EXTRA_HEADER[0]) {
    sheet.getRange(1, MEAL_EXTRA_COL, 1, MEAL_EXTRA_HEADER.length).setValues([MEAL_EXTRA_HEADER]);
  }
  return sheet;
}

/** 写真の保存先フォルダ（無ければ作る）。ID は Script Properties にキャッシュする。 */
function photoFolder_() {
  const props = PropertiesService.getScriptProperties();
  const cached = props.getProperty('PHOTO_FOLDER_ID');
  if (cached) {
    try {
      return DriveApp.getFolderById(cached);
    } catch (err) {
      props.deleteProperty('PHOTO_FOLDER_ID'); // 削除された場合は作り直す
    }
  }
  const it = DriveApp.getFoldersByName(PHOTO_FOLDER_NAME);
  const folder = it.hasNext() ? it.next() : DriveApp.createFolder(PHOTO_FOLDER_NAME);
  props.setProperty('PHOTO_FOLDER_ID', folder.getId());
  return folder;
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

/** カンマ・改行区切りの写真URLを配列にする。 */
function photoList_(value) {
  return String(value || '')
    .split(/[,\n]/)
    .map(function (v) { return v.trim(); })
    .filter(function (v) { return v.length > 0; });
}

/** 既存の写真URLに1枚足す（重複は足さない）。 */
function appendPhoto_(existing, url) {
  const list = photoList_(existing);
  if (list.indexOf(url) < 0) list.push(url);
  return list.join(',');
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
