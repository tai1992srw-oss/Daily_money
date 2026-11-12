/**
 * デビット利用通知メール（ドコモSMTBネット銀行）→ Zaim 自動登録。
 * Google Apps Script 用。Parser.js と一緒に同じプロジェクトへ貼り付けて使う。
 *
 * 必要なもの:
 *   - ライブラリ OAuth1（ID: 1CXDCY5sqT9ph64fFwSzVtXnbjpSfWdRymafDrtIZ7Z_hwysTY7IIhi7s）
 *   - スクリプト プロパティ ZAIM_CONSUMER_KEY / ZAIM_CONSUMER_SECRET
 *   - （任意）ZAIM_ACCOUNT_NAME … 出金元にしたい Zaim の口座名
 */

var MAIL_FROM = 'post_master@netbk.co.jp';
var LOOKBACK_DAYS = 3;          // 何日前までのメールを見るか
var MAX_PER_RUN = 40;           // 1 回の実行で登録する上限
var DEFAULT_CATEGORY = 'その他';
var DEFAULT_GENRE = '未分類';
var RULE_SHEET = 'ルール';
var LOG_SHEET = 'ログ';
var LOG_HEADER = ['処理日時', '種別', '承認番号', '利用日', '店名', '金額', 'カテゴリ', 'ジャンル', 'ZaimID', 'メッセージID', '結果'];

var SEED_RULES = [
  ['LAWSON', '食費', '食料品', 'ローソン'],
  ['SEVEN-ELEVEN', '食費', '食料品', 'セブン-イレブン'],
  ['FAMILYMART', '食費', '食料品', 'ファミリーマート'],
  ['ROCKETNOW', '食費', 'その他', 'Rocket Now'],
  ['EAST JAPAN RAILWAY', '交通', '電車', 'JR東日本'],
  ['SUICA', '交通', '電車', 'Suica チャージ'],
  ['HELLO CYCLING', '交通', 'その他', 'HELLO CYCLING'],
  ['SOKA KENKO CENTER', 'エンタメ', 'レジャー', '草加健康センター']
];

// ---------- 初期設定 ----------

/** 最初に 1 回実行する。記録用スプレッドシートと 15 分おきのトリガーを作る。 */
function setup() {
  var ss = getSpreadsheet_();
  Logger.log('ルールとログのシート: ' + ss.getUrl());

  var exists = ScriptApp.getProjectTriggers().some(function (t) {
    return t.getHandlerFunction() === 'syncDebitMails';
  });
  if (!exists) ScriptApp.newTrigger('syncDebitMails').timeBased().everyMinutes(15).create();

  var service = getZaimService_();
  Logger.log(service.hasAccess() ? 'Zaim 認証済みです。' : 'Zaim 未認証です。authorize() を実行してください。');
}

/** Zaim の認証 URL をログに出す。開いて許可すると連携が完了する。 */
function authorize() {
  var service = getZaimService_();
  if (service.hasAccess()) {
    Logger.log('すでに認証済みです。');
    return;
  }
  Logger.log('このURLを開いて Zaim で許可してください: ' + service.authorize());
}

function authCallback(request) {
  var ok = getZaimService_().handleCallback(request);
  return HtmlService.createHtmlOutput(ok ? 'Zaim との連携が完了しました。このタブは閉じて大丈夫です。' : '連携に失敗しました。');
}

/** 認証をやり直したいときに使う。 */
function resetAuth() {
  getZaimService_().reset();
}

function getZaimService_() {
  var props = PropertiesService.getScriptProperties();
  var key = props.getProperty('ZAIM_CONSUMER_KEY');
  var secret = props.getProperty('ZAIM_CONSUMER_SECRET');
  if (!key || !secret) throw new Error('スクリプト プロパティ ZAIM_CONSUMER_KEY / ZAIM_CONSUMER_SECRET を設定してください');
  return OAuth1.createService('zaim')
    .setRequestTokenUrl('https://api.zaim.net/v2/auth/request')
    .setAuthorizationUrl('https://auth.zaim.net/users/auth')
    .setAccessTokenUrl('https://api.zaim.net/v2/auth/access')
    .setConsumerKey(key)
    .setConsumerSecret(secret)
    .setCallbackFunction('authCallback')
    .setPropertyStore(PropertiesService.getUserProperties());
}

function getSpreadsheet_() {
  var props = PropertiesService.getScriptProperties();
  var id = props.getProperty('SHEET_ID');
  if (id) return SpreadsheetApp.openById(id);

  var ss = SpreadsheetApp.create('Zaimデビット連携');
  var rules = ss.getSheets()[0].setName(RULE_SHEET);
  rules.appendRow(['キーワード（店名に含まれる文字）', 'カテゴリ', 'ジャンル', '表示名（空欄ならメールの店名のまま）']);
  rules.getRange(2, 1, SEED_RULES.length, 4).setValues(SEED_RULES);
  rules.setFrozenRows(1);
  var log = ss.insertSheet(LOG_SHEET);
  log.appendRow(LOG_HEADER);
  log.setFrozenRows(1);
  props.setProperty('SHEET_ID', ss.getId());
  return ss;
}

// ---------- 本体 ----------

/** トリガーから 15 分おきに呼ばれる。 */
function syncDebitMails() {
  run_(false);
}

/** Zaim には何も送らず、メールの読み取りと振り分け結果だけをログに出す。 */
function dryRun() {
  run_(true);
}

function run_(dry) {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) return;
  try {
    var ss = getSpreadsheet_();
    var logSheet = ss.getSheetByName(LOG_SHEET);
    var logRows = logSheet.getDataRange().getValues().slice(1);
    var done = {};
    logRows.forEach(function (r) { done[r[9]] = true; });

    var messages = [];
    GmailApp.search('from:' + MAIL_FROM + ' subject:デビットカード newer_than:' + LOOKBACK_DAYS + 'd').forEach(function (thread) {
      thread.getMessages().forEach(function (m) {
        if (m.getFrom().indexOf(MAIL_FROM) !== -1 && !done[m.getId()]) messages.push(m);
      });
    });
    messages.sort(function (a, b) { return a.getDate() - b.getDate(); });
    messages = messages.slice(0, MAX_PER_RUN);
    if (!messages.length) return;

    var rules = ss.getSheetByName(RULE_SHEET).getDataRange().getValues().slice(1).map(function (r) {
      return { keyword: r[0], category: r[1], genre: r[2], displayName: r[3] };
    });
    var master = dry ? null : loadMaster_();

    messages.forEach(function (m) {
      var tx = parseDebitMail(m.getSubject(), m.getPlainBody());
      if (tx.kind === 'ignore') {
        // 利用不可・メンテナンス等。読み取り失敗のときだけ記録を残す。
        if (tx.reason && !dry) appendLog_(logSheet, logRows, ['', '', '', '', '', '', '', '', m.getId(), '要確認: ' + tx.reason]);
        else if (!dry) appendLog_(logSheet, logRows, ['対象外', '', '', '', '', '', '', '', m.getId(), 'スキップ']);
        return;
      }
      var row = handle_(tx, rules, master, logRows, dry);
      if (dry) Logger.log(row.join(' | '));
      else appendLog_(logSheet, logRows, row.slice(0, 8).concat([m.getId(), row[8]]));
    });
  } finally {
    lock.releaseLock();
  }
}

/** 1 件処理して [種別, 承認番号, 利用日, 店名, 金額, カテゴリ, ジャンル, ZaimID, 結果] を返す。 */
function handle_(tx, rules, master, logRows, dry) {
  var rule = matchRule(tx.store, rules);
  var storeName = (rule && rule.displayName) || tx.store || '（店名なし）';
  var category = rule ? rule.category : DEFAULT_CATEGORY;
  var genre = rule ? rule.genre : DEFAULT_GENRE;
  var label = { use: '利用', refund: '返金', settle: '売上確定' }[tx.kind];
  var base = [label, tx.approval, tx.date, storeName, tx.amount];

  // 同じ承認番号で登録済みの利用（返金・売上確定の突き合わせ用）
  // シートは「021928」を数値に変えてしまうので数値で比べる。000000 は承認なしの取引。
  var prior = null;
  var approvalNo = Number(tx.approval);
  for (var i = logRows.length - 1; approvalNo && i >= Math.max(0, logRows.length - 500) && !prior; i--) {
    var r = logRows[i];
    if (r[1] === '利用' && Number(r[2]) === approvalNo && r[8] && r[10] === '登録') prior = r;
  }

  if (tx.currency !== 'JPY') return base.concat(['', '', '', '要確認: 外貨（' + tx.currency + '）のため未登録']);

  if (tx.kind === 'refund') {
    if (!prior || Number(prior[5]) !== tx.amount) return base.concat(['', '', '', '要確認: 対応する登録が見つからず未処理']);
    if (!dry) zaim_('delete', 'home/money/payment/' + prior[8]);
    return base.concat(['', '', prior[8], dry ? '（テスト）削除予定' : '返金のため削除']);
  }

  if (tx.kind === 'settle' && prior) {
    // 利用時に登録済み。差額調整の可能性があるので金額が違うときだけ知らせる。
    var same = Number(prior[5]) === tx.amount;
    return base.concat(['', '', prior[8], same ? '登録済みのためスキップ' : '要確認: 確定金額が利用時（' + prior[5] + '円）と違います']);
  }

  if (dry) return base.concat([category, genre, '', rule ? '（テスト）登録予定' : '（テスト）登録予定・ルールなし']);

  var ids = resolveIds_(master, category, genre);
  var params = {
    mapping: 1,
    category_id: ids.categoryId,
    genre_id: ids.genreId,
    amount: tx.amount,
    date: tx.date,
    place: storeName.slice(0, 100),
    comment: 'デビット自動登録 #' + tx.approval + (tx.kind === 'settle' ? '（売上確定）' : '')
  };
  if (master.accountId) params.from_account_id = master.accountId;
  var res = zaim_('post', 'home/money/payment', params);
  return base.concat([ids.categoryName, ids.genreName, res.money.id, '登録']);
}

function appendLog_(sheet, logRows, cols) {
  var row = [new Date()].concat(cols);
  sheet.appendRow(row);
  logRows.push(row);
}

// ---------- Zaim API ----------

function zaim_(method, path, params) {
  var service = getZaimService_();
  if (!service.hasAccess()) throw new Error('Zaim 未認証です。authorize() を実行してください');
  var url = 'https://api.zaim.net/v2/' + path;
  var options = { method: method, muteHttpExceptions: true };
  if (params && method === 'get') {
    url += '?' + Object.keys(params).map(function (k) {
      return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
    }).join('&');
  } else if (params) {
    options.payload = params;
  }
  var res = service.fetch(url, options);
  if (res.getResponseCode() >= 300) {
    throw new Error('Zaim API エラー ' + res.getResponseCode() + ' (' + path + '): ' + res.getContentText().slice(0, 300));
  }
  return JSON.parse(res.getContentText());
}

function loadMaster_() {
  var master = {
    categories: zaim_('get', 'home/category', { mapping: 1 }).categories.filter(function (c) {
      return c.mode === 'payment' && c.active !== -1;
    }),
    genres: zaim_('get', 'home/genre', { mapping: 1 }).genres.filter(function (g) { return g.active !== -1; }),
    accountId: null
  };
  var accountName = PropertiesService.getScriptProperties().getProperty('ZAIM_ACCOUNT_NAME');
  if (accountName) {
    var hit = zaim_('get', 'home/account', { mapping: 1 }).accounts.filter(function (a) {
      return a.name === accountName && a.active !== -1;
    })[0];
    if (!hit) throw new Error('Zaim に口座「' + accountName + '」が見つかりません');
    master.accountId = hit.id;
  }
  return master;
}

/** カテゴリ名・ジャンル名を ID に直す。見つからなければ既定値、それも無ければ先頭のものを使う。 */
function resolveIds_(master, categoryName, genreName) {
  function byName(list, name) {
    return list.filter(function (x) { return x.name === name; })[0];
  }
  var cat = byName(master.categories, categoryName) || byName(master.categories, DEFAULT_CATEGORY) || master.categories[0];
  var genres = master.genres.filter(function (g) { return g.category_id === cat.id; });
  var genre = byName(genres, genreName) || byName(genres, DEFAULT_GENRE) || byName(genres, 'その他') || genres[0];
  if (!genre) throw new Error('カテゴリ「' + cat.name + '」にジャンルがありません');
  return { categoryId: cat.id, categoryName: cat.name, genreId: genre.id, genreName: genre.name };
}

// ---------- 診断 ----------

/**
 * 口座連携で入った「デビット」の記録が API から見えるかを調べる。
 * 見えるなら、新しく登録せずにその記録を直接書き換える方式にできる（二重計上が起きない）。
 */
function checkLinked() {
  var from = Utilities.formatDate(new Date(Date.now() - 7 * 86400000), 'Asia/Tokyo', 'yyyy-MM-dd');
  var list = zaim_('get', 'home/money', { mapping: 1, start_date: from, limit: 100 }).money;
  var linked = list.filter(function (m) { return String(m.place || '').indexOf('デビット') !== -1; });
  Logger.log('直近7日の記録: ' + list.length + ' 件 / うち店名が「デビット」: ' + linked.length + ' 件');
  linked.slice(0, 5).forEach(function (m) { Logger.log(JSON.stringify(m)); });
  if (!linked.length && list.length) Logger.log('参考（先頭1件）: ' + JSON.stringify(list[0]));
}
