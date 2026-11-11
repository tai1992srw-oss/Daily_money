/**
 * デビット通知メールの解析と、店名 → カテゴリの振り分け。
 * Google サービスに依存しない純粋なロジックだけを置く（Node でテストできる）。
 */

/** 半角カナ・全角英数をそろえ、大文字にして前後の空白を落とす。 */
function normalizeText(s) {
  return String(s || '').normalize('NFKC').toUpperCase().replace(/\s+/g, ' ').trim();
}

function pickField_(body, labels) {
  for (var i = 0; i < labels.length; i++) {
    var m = body.match(new RegExp('^[ \\t\\u3000]*' + labels[i] + '[ \\t\\u3000]*[：:][ \\t\\u3000]*(.*)$', 'm'));
    if (m) return m[1].trim();
  }
  return '';
}

/**
 * 件名と本文から取引を取り出す。
 * kind: 'use'(利用) / 'refund'(返金) / 'settle'(売上確定による引落し・入金) / 'ignore'(対象外)
 */
function parseDebitMail(subject, body) {
  var kind = 'ignore';
  if (/ご利用のお知らせ/.test(subject)) kind = 'use';
  else if (/ご返金のお知らせ/.test(subject)) kind = 'refund';
  else if (/引落しまたは入金処理のお知らせ/.test(subject)) kind = 'settle';
  if (kind === 'ignore') return { kind: kind };

  var rawDate = pickField_(body, ['利用日時', '利用日']);
  var dm = rawDate.match(/(\d{4})\/(\d{1,2})\/(\d{1,2})/);
  var rawAmount = pickField_(body, ['引落金額', '利用金額']).replace(/,/g, '');
  var amount = Math.round(parseFloat(rawAmount));

  if (!dm || !isFinite(amount)) return { kind: 'ignore', reason: '日付または金額を読み取れませんでした' };

  return {
    kind: kind,
    approval: pickField_(body, ['承認番号']),
    date: dm[1] + '-' + ('0' + dm[2]).slice(-2) + '-' + ('0' + dm[3]).slice(-2),
    store: pickField_(body, ['利用加盟店']),
    currency: pickField_(body, ['引落通貨', '利用通貨']) || 'JPY',
    amount: amount
  };
}

/**
 * rules: [{ keyword, category, genre, displayName }] を上から順に見て、
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
  module.exports = { normalizeText: normalizeText, parseDebitMail: parseDebitMail, matchRule: matchRule };
}
