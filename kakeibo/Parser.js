/**
 * 銀行（ドコモSMTBネット銀行）からの通知メールの解析と、店名 → カテゴリの振り分け。
 * Google サービスに依存しない純粋なロジックだけを置く（Node でテストできる）。
 */

/** 半角カナ・全角英数をそろえ、大文字にして前後の空白を落とす。 */
function normalizeText(s) {
  return String(s || '').normalize('NFKC').toUpperCase().replace(/\s+/g, ' ').trim();
}

function pad2_(n) {
  return ('0' + n).slice(-2);
}

function pickField_(body, labels) {
  for (var i = 0; i < labels.length; i++) {
    var m = body.match(new RegExp('^[ \\t\\u3000・]*' + labels[i] + '[ \\t\\u3000]*[：:][ \\t\\u3000]*(.*)$', 'm'));
    if (m) return m[1].trim();
  }
  return '';
}

/** 「2026年11月6日午前10時5分」のような和文の日時を { date, time } にする。時刻が無ければ time は ''。 */
function parseJaDateTime_(text) {
  var m = String(text).match(/(\d{4})年(\d{1,2})月(\d{1,2})日(?:(午前|午後)(\d{1,2})時(\d{1,2})分)?/);
  if (!m) return null;
  var time = '';
  if (m[4]) {
    var h = Number(m[5]) % 12 + (m[4] === '午後' ? 12 : 0);
    time = pad2_(h) + ':' + pad2_(m[6]);
  }
  return { date: m[1] + '-' + pad2_(m[2]) + '-' + pad2_(m[3]), time: time };
}

/**
 * 件名と本文から取引を取り出す。
 * kind:
 *   'use'      デビット利用          'refund'   デビット返金
 *   'settle'   デビット売上確定      'deposit'  振込入金（金額なし）
 *   'withdraw' サービスへの出金（金額なし）  'transfer' 振込受付（金額なし）
 *   'ignore'   対象外
 */
function parseBankMail(subject, body) {
  subject = String(subject || '');
  body = String(body || '');
  if (subject.indexOf('デビットカード') !== -1) return parseDebitMail_(subject, body);

  var when = parseJaDateTime_(body);
  if (/振込入金のお知らせ/.test(subject) && when) {
    return { kind: 'deposit', date: when.date, time: when.time, name: '振込入金' };
  }
  if (/出金のお知らせ/.test(subject) && when) {
    var service = pickField_(body, ['サービス名']).normalize('NFKC');
    return { kind: 'withdraw', date: when.date, time: when.time, name: service || '出金' };
  }
  if (/振込受付のお知らせ/.test(subject) && when) {
    return { kind: 'transfer', date: when.date, time: when.time, name: '振込', ref: pickField_(body, ['受付番号']) };
  }
  return { kind: 'ignore' };
}

function parseDebitMail_(subject, body) {
  var kind = 'ignore';
  if (/ご利用のお知らせ/.test(subject)) kind = 'use';
  else if (/ご返金のお知らせ/.test(subject)) kind = 'refund';
  else if (/引落しまたは入金処理のお知らせ/.test(subject)) kind = 'settle';
  if (kind === 'ignore') return { kind: kind };

  var rawDate = pickField_(body, ['利用日時', '利用日']);
  var dm = rawDate.match(/(\d{4})\/(\d{1,2})\/(\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?/);
  var rawAmount = pickField_(body, ['引落金額', '利用金額']).replace(/,/g, '');
  var amount = Math.round(parseFloat(rawAmount));
  if (!dm || !isFinite(amount)) return { kind: 'ignore', reason: '日付または金額を読み取れませんでした' };

  return {
    kind: kind,
    approval: pickField_(body, ['承認番号']),
    date: dm[1] + '-' + pad2_(dm[2]) + '-' + pad2_(dm[3]),
    time: dm[4] ? pad2_(dm[4]) + ':' + dm[5] : '',
    store: pickField_(body, ['利用加盟店']),
    currency: pickField_(body, ['引落通貨', '利用通貨']) || 'JPY',
    amount: amount
  };
}

/**
 * rules: [{ keyword, category, displayName }] を上から順に見て、
 * 店名にキーワードが含まれる最初のルールを返す。なければ null。
 */
function matchRule(store, rules) {
  var target = normalizeText(store);
  if (!target) return null;
  for (var i = 0; i < rules.length; i++) {
    var key = normalizeText(rules[i].keyword);
    if (key && target.indexOf(key) !== -1) return rules[i];
  }
  return null;
}

if (typeof module !== 'undefined') {
  module.exports = { normalizeText: normalizeText, parseBankMail: parseBankMail, matchRule: matchRule };
}
