/**
 * デビット利用通知メール（ドコモSMTBネット銀行）→ スプレッドシート家計簿。
 * Google Apps Script 用。Parser.js と一緒に同じプロジェクトへ貼り付けて使う。
 * 外部サービスの登録やキーは不要（Gmail とスプレッドシートだけを使う）。
 */

var MAIL_FROM = 'post_master@netbk.co.jp';
var LOOKBACK_DAYS = 3;          // 定期実行で何日前までのメールを見るか
var BACKFILL_DAYS = 90;         // backfill() でさかのぼる日数
var DEFAULT_CATEGORY = '未分類';
var RULE_SHEET = 'ルール';
var DETAIL_SHEET = '明細';
var SUMMARY_SHEET = '月別集計';
var DETAIL_HEADER = ['利用日', '月', '店名', '金額', 'カテゴリ', '種別', '承認番号', 'メール上の店名', 'メッセージID', 'メモ'];
// 明細シートの列番号（0 始まり）
var COL = { date: 0, month: 1, store: 2, amount: 3, category: 4, kind: 5, approval: 6, rawStore: 7, messageId: 8, memo: 9 };

var SEED_RULES = [
  ['LAWSON', '食費', 'ローソン'],
  ['SEVEN-ELEVEN', '食費', 'セブン-イレブン'],
  ['FAMILYMART', '食費', 'ファミリーマート'],
  ['ROCKETNOW', '食費', 'Rocket Now'],
  ['EAST JAPAN RAILWAY', '交通', 'JR東日本'],
  ['SUICA', '交通', 'Suica チャージ'],
  ['HELLO CYCLING', '交通', 'HELLO CYCLING'],
  ['SOKA KENKO CENTER', '娯楽', '草加健康センター']
];

/** 最初に 1 回実行する。スプレッドシートと 15 分おきのトリガーを作る。 */
function setup() {
  var ss = getSpreadsheet_();
  var exists = ScriptApp.getProjectTriggers().some(function (t) {
    return t.getHandlerFunction() === 'syncDebitMails';
  });
  if (!exists) ScriptApp.newTrigger('syncDebitMails').timeBased().everyMinutes(15).create();
  Logger.log('家計簿のシート: ' + ss.getUrl());
}

/** トリガーから 15 分おきに呼ばれる。 */
function syncDebitMails() {
  run_(LOOKBACK_DAYS);
}

/** 過去分をまとめて取り込む。取り込み済みのメールは飛ばすので何度実行してもよい。 */
function backfill() {
  run_(BACKFILL_DAYS);
}

/** 「未分類」の行に、いまのルールを当て直す。ルールを足したあとに実行する。 */
function reclassify() {
  var ss = getSpreadsheet_();
  var sheet = ss.getSheetByName(DETAIL_SHEET);
  var rules = loadRules_(ss);
  var last = sheet.getLastRow();
  if (last < 2) return;
  // 店名とカテゴリの 2 列だけ書き戻す（ほかの列の書式を崩さない）
  var rows = sheet.getRange(2, 1, last - 1, DETAIL_HEADER.length).getValues();
  var out = [];
  var changed = 0;
  rows.forEach(function (r) {
    var rule = r[COL.category] === DEFAULT_CATEGORY ? matchRule(r[COL.rawStore], rules) : null;
    if (rule) {
      changed++;
      out.push([rule.displayName || r[COL.store], '', rule.category]);
    } else {
      out.push([r[COL.store], '', r[COL.category]]);
    }
  });
  if (changed) {
    sheet.getRange(2, COL.store + 1, out.length, 1).setValues(out.map(function (o) { return [o[0]]; }));
    sheet.getRange(2, COL.category + 1, out.length, 1).setValues(out.map(function (o) { return [o[2]]; }));
  }
  Logger.log(changed + ' 行を振り分け直しました');
}

function getSpreadsheet_() {
  var props = PropertiesService.getScriptProperties();
  var id = props.getProperty('SHEET_ID');
  if (id) return SpreadsheetApp.openById(id);

  var ss = SpreadsheetApp.create('デビット家計簿');
  var rules = ss.getSheets()[0].setName(RULE_SHEET);
  rules.appendRow(['キーワード（メール上の店名に含まれる文字）', 'カテゴリ', '表示名（空欄ならメールの店名のまま）']);
  rules.getRange(2, 1, SEED_RULES.length, 3).setValues(SEED_RULES);
  rules.setFrozenRows(1);

  var detail = ss.insertSheet(DETAIL_SHEET);
  detail.appendRow(DETAIL_HEADER);
  detail.setFrozenRows(1);
  // 「2026-11」「021928」が日付や数値に化けないよう文字列の列にする
  detail.getRange('B:B').setNumberFormat('@');
  detail.getRange('G:G').setNumberFormat('@');
  detail.getRange('A:A').setNumberFormat('yyyy-mm-dd');

  var summary = ss.insertSheet(SUMMARY_SHEET);
  summary.getRange('A1').setFormula(
    "=QUERY('" + DETAIL_SHEET + "'!A:E,\"select E, sum(D) where A is not null group by E pivot B label E 'カテゴリ'\",1)");

  props.setProperty('SHEET_ID', ss.getId());
  return ss;
}

function loadRules_(ss) {
  return ss.getSheetByName(RULE_SHEET).getDataRange().getValues().slice(1).map(function (r) {
    return { keyword: r[0], category: r[1], displayName: r[2] };
  });
}

function run_(days) {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) return;
  try {
    var ss = getSpreadsheet_();
    var sheet = ss.getSheetByName(DETAIL_SHEET);
    var existing = sheet.getDataRange().getValues().slice(1);
    var done = {};
    existing.forEach(function (r) { done[r[COL.messageId]] = true; });

    var messages = [];
    var start = 0;
    while (true) {
      var threads = GmailApp.search('from:' + MAIL_FROM + ' subject:デビットカード newer_than:' + days + 'd', start, 100);
      threads.forEach(function (thread) {
        thread.getMessages().forEach(function (m) {
          if (m.getFrom().indexOf(MAIL_FROM) !== -1 && !done[m.getId()]) messages.push(m);
        });
      });
      if (threads.length < 100) break;
      start += 100;
    }
    messages.sort(function (a, b) { return a.getDate() - b.getDate(); });

    var rules = loadRules_(ss);
    var added = [];
    messages.forEach(function (m) {
      var tx = parseDebitMail(m.getSubject(), m.getPlainBody());
      if (tx.kind === 'ignore') return;   // 利用不可・メンテナンス等
      var row = buildRow_(tx, rules, existing.concat(added));
      if (!row) return;
      row[COL.messageId] = m.getId();
      added.push(row);
    });
    if (added.length) {
      sheet.getRange(sheet.getLastRow() + 1, 1, added.length, DETAIL_HEADER.length).setValues(added);
    }
    Logger.log(added.length + ' 件を追加しました');
  } finally {
    lock.releaseLock();
  }
}

/** 取引 1 件を明細の 1 行にする。書く必要がなければ null。 */
function buildRow_(tx, rules, rows) {
  var rule = matchRule(tx.store, rules);
  var row = [];
  row[COL.date] = tx.date;
  row[COL.month] = tx.date.slice(0, 7);
  row[COL.store] = (rule && rule.displayName) || tx.store || '（店名なし）';
  row[COL.amount] = tx.amount;
  row[COL.category] = rule ? rule.category : DEFAULT_CATEGORY;
  row[COL.kind] = '利用';
  row[COL.approval] = tx.approval;
  row[COL.rawStore] = tx.store;
  row[COL.messageId] = '';
  row[COL.memo] = '';

  // 同じ承認番号の利用（000000 は承認なしの取引なので突き合わせない）
  var prior = null;
  if (Number(tx.approval)) {
    for (var i = rows.length - 1; i >= 0 && !prior; i--) {
      if (rows[i][COL.kind] === '利用' && Number(rows[i][COL.approval]) === Number(tx.approval)) prior = rows[i];
    }
  }

  if (tx.currency !== 'JPY') {
    row[COL.amount] = 0;
    row[COL.kind] = '要確認';
    row[COL.memo] = '外貨（' + tx.currency + ' ' + tx.amount + '）のため金額未記入';
    return row;
  }

  if (tx.kind === 'refund') {
    row[COL.amount] = -tx.amount;
    row[COL.kind] = '返金';
    if (prior) {
      row[COL.store] = prior[COL.store];
      row[COL.category] = prior[COL.category];
    }
    return row;
  }

  if (tx.kind === 'settle') {
    if (prior && Number(prior[COL.amount]) === tx.amount) return null;   // 利用時に記録済み
    if (prior) {
      row[COL.amount] = 0;
      row[COL.kind] = '要確認';
      row[COL.memo] = '確定金額 ' + tx.amount + ' 円が利用時（' + prior[COL.amount] + ' 円）と違います';
      return row;
    }
    row[COL.kind] = '利用（売上確定）';
  }
  return row;
}

if (typeof module !== 'undefined') {
  module.exports = { buildRow_: buildRow_, COL: COL };
}
