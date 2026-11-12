/**
 * Apps Script 側のつなぎ込み。画面の配信、スプレッドシートへの保存、Gmail の読み取り。
 * 家計簿の中身のロジックは Logic.js / Parser.js にある。
 */

var MAIL_FROM = 'post_master@netbk.co.jp';
var ENTRY_SHEET = '取引';
var RULE_SHEET = 'ルール';
var SETTING_SHEET = '設定';
var RULE_FIELDS = ['keyword', 'category', 'displayName'];

/** 最初に 1 回実行する。保存用のスプレッドシートと、15 分おきのメール取り込みを用意する。 */
function setup() {
  var ss = getSpreadsheet_();
  var exists = ScriptApp.getProjectTriggers().some(function (t) {
    return t.getHandlerFunction() === 'syncTrigger';
  });
  if (!exists) ScriptApp.newTrigger('syncTrigger').timeBased().everyMinutes(15).create();
  Logger.log('データの保存先: ' + ss.getUrl());
}

/** 15 分おきに呼ばれる。 */
function syncTrigger() {
  withLock_(function () {
    syncMails(sheetStore_(), gmail_(), 3);
  });
}

function doGet() {
  return HtmlService.createHtmlOutputFromFile('index')
    .setTitle('家計簿')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1');
}

/** 画面から呼ばれる。引数も戻り値も JSON 文字列。 */
function api(action, payloadJson) {
  return withLock_(function () {
    var state = handleApi(sheetStore_(), gmail_(), action, JSON.parse(payloadJson || '{}'));
    return JSON.stringify(state);
  });
}

function withLock_(fn) {
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}

// ---------- 保存先（スプレッドシート） ----------

function getSpreadsheet_() {
  var props = PropertiesService.getScriptProperties();
  var id = props.getProperty('SHEET_ID');
  if (id) return SpreadsheetApp.openById(id);

  var ss = SpreadsheetApp.create('家計簿データ');
  var entries = ss.getSheets()[0].setName(ENTRY_SHEET);
  initSheet_(entries, ENTRY_FIELDS);
  var rules = initSheet_(ss.insertSheet(RULE_SHEET), RULE_FIELDS);
  rules.getRange(2, 1, SEED_RULES.length, RULE_FIELDS.length).setValues(SEED_RULES.map(function (r) {
    return RULE_FIELDS.map(function (f) { return r[f]; });
  }));
  initSheet_(ss.insertSheet(SETTING_SHEET), ['key', 'value']);
  props.setProperty('SHEET_ID', ss.getId());
  return ss;
}

function initSheet_(sheet, header) {
  // 「021928」や「2026-11-09」が数値や日付に化けないよう、全体を文字列にする
  sheet.getRange(1, 1, sheet.getMaxRows(), header.length).setNumberFormat('@');
  sheet.getRange(1, 1, 1, header.length).setValues([header]);
  sheet.setFrozenRows(1);
  return sheet;
}

function readRows_(sheet, fields) {
  var last = sheet.getLastRow();
  if (last < 2) return [];
  return sheet.getRange(2, 1, last - 1, fields.length).getDisplayValues().map(function (row) {
    var o = {};
    fields.forEach(function (f, i) { o[f] = row[i]; });
    return o;
  });
}

function writeRows_(sheet, fields, rows) {
  var last = sheet.getLastRow();
  if (last > 1) sheet.getRange(2, 1, last - 1, fields.length).clearContent();
  if (!rows.length) return;
  var need = rows.length + 1 - sheet.getMaxRows();
  if (need > 0) {
    sheet.insertRowsAfter(sheet.getMaxRows(), need);
    sheet.getRange(1, 1, sheet.getMaxRows(), fields.length).setNumberFormat('@');
  }
  sheet.getRange(2, 1, rows.length, fields.length).setValues(rows.map(function (r) {
    return fields.map(function (f) { return r[f] === undefined || r[f] === null ? '' : String(r[f]); });
  }));
}

function sheetStore_() {
  var ss = getSpreadsheet_();
  return {
    load: function () {
      var entries = readRows_(ss.getSheetByName(ENTRY_SHEET), ENTRY_FIELDS);
      entries.forEach(function (e) { if (e.amount !== '') e.amount = Number(e.amount); });
      var settings = {};
      readRows_(ss.getSheetByName(SETTING_SHEET), ['key', 'value']).forEach(function (r) { settings[r.key] = r.value; });
      return { entries: entries, rules: readRows_(ss.getSheetByName(RULE_SHEET), RULE_FIELDS), settings: settings };
    },
    saveEntries: function (entries) { writeRows_(ss.getSheetByName(ENTRY_SHEET), ENTRY_FIELDS, entries); },
    saveRules: function (rules) { writeRows_(ss.getSheetByName(RULE_SHEET), RULE_FIELDS, rules); },
    saveSetting: function (key, value) {
      var sheet = ss.getSheetByName(SETTING_SHEET);
      var rows = readRows_(sheet, ['key', 'value']).filter(function (r) { return r.key !== key; });
      rows.push({ key: key, value: value });
      writeRows_(sheet, ['key', 'value'], rows);
    }
  };
}

// ---------- メールの取得元（Gmail） ----------

function gmail_() {
  return {
    list: function (days, isDone) {
      var found = [];
      var start = 0;
      while (true) {
        var threads = GmailApp.search('from:' + MAIL_FROM + ' newer_than:' + days + 'd', start, 100);
        threads.forEach(function (thread) {
          thread.getMessages().forEach(function (m) {
            if (m.getFrom().indexOf(MAIL_FROM) === -1 || isDone(m.getId())) return;
            found.push({ id: m.getId(), at: m.getDate().getTime(), message: m });
          });
        });
        if (threads.length < 100) break;
        start += 100;
      }
      found.sort(function (a, b) { return a.at - b.at; });
      return found.map(function (f) {
        return { id: f.id, subject: f.message.getSubject(), body: f.message.getPlainBody() };
      });
    }
  };
}
