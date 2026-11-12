/**
 * 家計簿の中核ロジック。保存先（store）とメールの取得元（mail）は外から渡すので、
 * Apps Script 上でも Node のテストでも同じコードが動く。
 *
 * store: { load() -> { entries, rules, settings }, saveEntries(entries), saveRules(rules), saveSetting(key, value) }
 * mail:  { list(days, isDone) -> [{ id, subject, body }] 古い順 }
 */

if (typeof module !== 'undefined') {
  var parser_ = require('./Parser.js');
  var parseBankMail = parser_.parseBankMail;
  var matchRule = parser_.matchRule;
  var normalizeText = parser_.normalizeText;
}

var UNCATEGORIZED = '未分類';
var CATEGORIES = ['食費', '日用品', '交通', '交際費', '娯楽', '衣服・美容', '医療', '住まい・光熱', '通信', 'その他',
  UNCATEGORIZED, '振替・チャージ', '収入', '残高調整'];
// 口座残高には効くが、支出・収入の集計には入れないカテゴリ
var NON_SPENDING = ['振替・チャージ', '残高調整'];

var SEED_RULES = [
  { keyword: 'LAWSON', category: '食費', displayName: 'ローソン' },
  { keyword: 'SEVEN-ELEVEN', category: '食費', displayName: 'セブン-イレブン' },
  { keyword: 'FAMILYMART', category: '食費', displayName: 'ファミリーマート' },
  { keyword: 'ROCKETNOW', category: '食費', displayName: 'Rocket Now' },
  { keyword: 'EAST JAPAN RAILWAY', category: '交通', displayName: 'JR東日本' },
  { keyword: 'SUICA', category: '振替・チャージ', displayName: 'Suica チャージ' },
  { keyword: 'HELLO CYCLING', category: '交通', displayName: 'HELLO CYCLING' },
  { keyword: 'SOKA KENKO CENTER', category: '娯楽', displayName: '草加健康センター' },
  { keyword: 'PAYPAY', category: '振替・チャージ', displayName: 'PayPay チャージ' },
  { keyword: 'メルペイ', category: '振替・チャージ', displayName: 'メルペイ チャージ' }
];

var ENTRY_FIELDS = ['id', 'date', 'time', 'type', 'amount', 'name', 'category', 'method', 'source', 'status',
  'approval', 'rawStore', 'messageId', 'memo'];

function newId_() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

function blankEntry_() {
  var e = {};
  ENTRY_FIELDS.forEach(function (f) { e[f] = ''; });
  e.id = newId_();
  e.type = 'expense';
  e.method = '口座';
  e.status = 'ok';
  return e;
}

/** 金額が入っていて集計に使える記録か。 */
function hasAmount_(e) {
  return e.amount !== '' && e.amount !== null && isFinite(Number(e.amount));
}

/** 口座残高 = 口座払いの収入 − 支出（返金はマイナスの支出なので足し戻される）。 */
function computeBalance(entries) {
  return entries.reduce(function (sum, e) {
    if (e.method !== '口座' || !hasAmount_(e)) return sum;
    return sum + (e.type === 'income' ? 1 : -1) * Number(e.amount);
  }, 0);
}

function getState(store) {
  var data = store.load();
  return {
    entries: data.entries,
    rules: data.rules,
    categories: CATEGORIES,
    nonSpending: NON_SPENDING,
    balance: computeBalance(data.entries),
    lastSync: data.settings.lastSync || ''
  };
}

// ---------- メールの取り込み ----------

/** 同じ承認番号のデビット利用を探す（000000 は承認なしの取引なので突き合わせない）。 */
function findPriorUse_(entries, approval) {
  if (!Number(approval)) return null;
  for (var i = entries.length - 1; i >= 0; i--) {
    var e = entries[i];
    if (e.source === 'debit' && Number(e.amount) >= 0 && Number(e.approval) === Number(approval)) return e;
  }
  return null;
}

/**
 * メール 1 通分の取引を記録に反映する。
 * 新しい記録を返すか、既存の記録を書き換えて null を返す（changed に true を立てる）。
 */
function applyMail_(tx, messageId, entries, rules, flags) {
  var e = blankEntry_();
  e.date = tx.date;
  e.time = tx.time || '';
  e.messageId = messageId;

  if (tx.kind === 'deposit' || tx.kind === 'withdraw' || tx.kind === 'transfer') {
    var rule0 = matchRule(tx.name, rules);
    e.source = 'mail';
    e.status = 'pending';            // メールに金額が無いので入力待ち
    e.type = tx.kind === 'deposit' ? 'income' : 'expense';
    e.rawStore = tx.name;
    e.name = (rule0 && rule0.displayName) || tx.name;
    e.category = rule0 ? rule0.category : (tx.kind === 'deposit' ? '収入' : UNCATEGORIZED);
    if (tx.ref) e.memo = '受付番号 ' + tx.ref;
    return e;
  }

  var rule = matchRule(tx.store, rules);
  var prior = findPriorUse_(entries, tx.approval);
  e.source = 'debit';
  e.approval = tx.approval;
  e.rawStore = tx.store;
  e.name = (rule && rule.displayName) || tx.store || '（店名なし）';
  e.category = rule ? rule.category : UNCATEGORIZED;
  e.amount = tx.amount;

  if (tx.kind === 'refund') {
    e.amount = -tx.amount;
    e.memo = '返金';
    if (prior) {
      e.name = prior.name;
      e.category = prior.category;
    }
    return e;
  }

  if (tx.kind === 'settle') {
    if (tx.currency !== 'JPY') {
      if (prior) return null;        // 円での引落額は利用時の通知で記録済み
      e.amount = '';
      e.status = 'pending';
      e.memo = '外貨での確定（' + tx.currency + ' ' + tx.amount + '）';
      return e;
    }
    if (prior) {
      if (Number(prior.amount) !== tx.amount) {
        prior.memo = '確定金額に更新（利用時 ' + prior.amount + ' 円）';
        prior.amount = tx.amount;
        flags.changed = true;
      }
      return null;
    }
    e.memo = '売上確定の通知から記録';
  }
  return e;
}

/** 直近 days 日のメールを読み、まだ取り込んでいないものを記録に加える。追加件数を返す。 */
function syncMails(store, mail, days, nowIso) {
  var data = store.load();
  var done = {};
  data.entries.forEach(function (e) { if (e.messageId) done[e.messageId] = true; });

  var flags = { changed: false };
  var added = 0;
  mail.list(days, function (id) { return !!done[id]; }).forEach(function (m) {
    if (done[m.id]) return;
    var tx = parseBankMail(m.subject, m.body);
    if (tx.kind === 'ignore') return;
    var entry = applyMail_(tx, m.id, data.entries, data.rules, flags);
    if (!entry) return;
    data.entries.push(entry);
    done[m.id] = true;
    added++;
  });

  if (added || flags.changed) store.saveEntries(data.entries);
  store.saveSetting('lastSync', nowIso || new Date().toISOString());
  return added;
}

// ---------- 手入力・編集 ----------

function cleanEntry_(input, base) {
  var e = base || blankEntry_();
  ['date', 'time', 'name', 'category', 'memo'].forEach(function (f) {
    if (input[f] !== undefined) e[f] = String(input[f]).trim();
  });
  if (input.type === 'income' || input.type === 'expense') e.type = input.type;
  if (input.method === '口座' || input.method === 'その他') e.method = input.method;
  if (input.amount !== undefined) {
    var raw = String(input.amount).replace(/[,\s円¥]/g, '');
    e.amount = raw === '' ? '' : Math.round(Number(raw));
    if (e.amount !== '' && !isFinite(e.amount)) throw new Error('金額は数字で入力してください');
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(e.date)) throw new Error('日付を入力してください');
  if (!e.category) e.category = e.type === 'income' ? '収入' : UNCATEGORIZED;
  if (!e.name) e.name = e.category;
  if (!e.source) e.source = 'manual';
  // 金額が入れば入力待ちは解消
  e.status = e.amount === '' ? 'pending' : 'ok';
  return e;
}

/**
 * 記録を追加または更新する。
 * learn が true で、メール由来の店名（rawStore）がある記録なら、
 * 「この店はこのカテゴリ・この表示名」というルールを覚え、同じ店の他の記録にも当てる。
 */
function saveEntry(store, input, learn) {
  var data = store.load();
  var existing = null;
  data.entries.forEach(function (e) { if (e.id === input.id) existing = e; });
  if (input.id && !existing) throw new Error('記録が見つかりません。画面を更新してください');

  var entry = cleanEntry_(input, existing);
  if (!existing) data.entries.push(entry);

  if (learn && entry.rawStore) {
    var key = normalizeText(entry.rawStore);
    var rule = null;
    data.rules.forEach(function (r) { if (normalizeText(r.keyword) === key) rule = r; });
    if (!rule) {
      rule = { keyword: entry.rawStore, category: '', displayName: '' };
      data.rules.unshift(rule);      // 完全一致のルールを部分一致より先に当てる
    }
    rule.category = entry.category;
    rule.displayName = entry.name === entry.rawStore ? '' : entry.name;
    data.entries.forEach(function (e) {
      if (e !== entry && normalizeText(e.rawStore) === key) {
        e.category = entry.category;
        e.name = entry.name;
      }
    });
    store.saveRules(data.rules);
  }
  store.saveEntries(data.entries);
}

function deleteEntry(store, id) {
  var data = store.load();
  var rest = data.entries.filter(function (e) { return e.id !== id; });
  if (rest.length === data.entries.length) throw new Error('記録が見つかりません。画面を更新してください');
  store.saveEntries(rest);
}

/** 実際の口座残高を入力してもらい、差額を「残高調整」として記録する。 */
function setBalance(store, actual, today) {
  var target = Math.round(Number(String(actual).replace(/[,\s円¥]/g, '')));
  if (!isFinite(target)) throw new Error('残高は数字で入力してください');
  var data = store.load();
  var diff = target - computeBalance(data.entries);
  if (diff === 0) return;
  var e = blankEntry_();
  e.date = today;
  e.type = diff > 0 ? 'income' : 'expense';
  e.amount = Math.abs(diff);
  e.name = '残高調整';
  e.category = '残高調整';
  e.source = 'manual';
  data.entries.push(e);
  store.saveEntries(data.entries);
}

/** ルールを丸ごと置き換え、未分類の記録に当て直す。 */
function saveRules(store, rules) {
  var clean = (rules || []).map(function (r) {
    return {
      keyword: String(r.keyword || '').trim(),
      category: String(r.category || '').trim() || UNCATEGORIZED,
      displayName: String(r.displayName || '').trim()
    };
  }).filter(function (r) { return r.keyword; });

  var data = store.load();
  var changed = false;
  data.entries.forEach(function (e) {
    if (e.category !== UNCATEGORIZED || !e.rawStore) return;
    var rule = matchRule(e.rawStore, clean);
    if (!rule) return;
    e.category = rule.category;
    if (rule.displayName) e.name = rule.displayName;
    changed = true;
  });
  store.saveRules(clean);
  if (changed) store.saveEntries(data.entries);
}

/** 画面からの呼び出しの入口。処理後の最新状態を返す。 */
function handleApi(store, mail, action, payload, now) {
  payload = payload || {};
  var result = {};
  if (action === 'saveEntry') saveEntry(store, payload.entry || {}, !!payload.learn);
  else if (action === 'deleteEntry') deleteEntry(store, payload.id);
  else if (action === 'setBalance') setBalance(store, payload.actual, payload.today);
  else if (action === 'saveRules') saveRules(store, payload.rules);
  else if (action === 'sync') result.added = syncMails(store, mail, payload.days || 3, now);
  else if (action !== 'getState') throw new Error('不明な操作です: ' + action);
  var state = getState(store);
  state.added = result.added;
  return state;
}

if (typeof module !== 'undefined') {
  module.exports = {
    handleApi: handleApi, syncMails: syncMails, computeBalance: computeBalance,
    SEED_RULES: SEED_RULES, ENTRY_FIELDS: ENTRY_FIELDS, CATEGORIES: CATEGORIES
  };
}
