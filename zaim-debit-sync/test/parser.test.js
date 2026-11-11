const test = require('node:test');
const assert = require('node:assert');
const { parseDebitMail, matchRule, normalizeText } = require('../Parser.js');

const USE = ` 山田　太郎 さま

デビットカードのご利用がありました。

【取引情報】
承認番号 ： 774068
利用日時 ： 2026/11/09 05:56:51
利用加盟店 ： LAWSON
引落通貨 ： JPY
引落金額 ： 459.00
※利用日時は日本標準時刻となります。
`;

const REFUND = ` 山田　太郎 さま

下記お取引について返金いたしました。

【取引情報】
承認番号 ： 021928
利用日時 ： 2026/09/24 19:06:31
利用加盟店 ： EAST JAPAN RAILWAY CO.
引落通貨 ： JPY
引落金額 ： 14,180.00
　※利用日時は日本標準時刻となります。
`;

const SETTLE = ` 山田　太郎 さま

デビットカードの利用代金の引落しまたは入金を行いました。

【取引情報（売上確定データ）】
承認番号 ： 820118
利用日 ： 2026/10/23
利用加盟店 ： ｶ ﾊﾁﾖｳ
利用通貨 ： JPY
利用金額 ： 230.00
※差額調整が発生した場合、上記の利用金額と入出金の金額が異なる場合があります。
`;

test('利用のお知らせを読み取る', () => {
  assert.deepStrictEqual(parseDebitMail('【デビットカード】ご利用のお知らせ(ドコモSMTBネット銀行)', USE), {
    kind: 'use', approval: '774068', date: '2026-11-09', store: 'LAWSON', currency: 'JPY', amount: 459
  });
});

test('返金のお知らせはカンマ入りの金額も読める', () => {
  const tx = parseDebitMail('【デビットカード】ご返金のお知らせ(ドコモSMTBネット銀行)', REFUND);
  assert.strictEqual(tx.kind, 'refund');
  assert.strictEqual(tx.amount, 14180);
  assert.strictEqual(tx.store, 'EAST JAPAN RAILWAY CO.');
});

test('売上確定のお知らせは利用日・利用金額から読む', () => {
  const tx = parseDebitMail('【デビットカード】引落しまたは入金処理のお知らせ(ドコモSMTBネット銀行)', SETTLE);
  assert.deepStrictEqual(tx, {
    kind: 'settle', approval: '820118', date: '2026-10-23', store: 'ｶ ﾊﾁﾖｳ', currency: 'JPY', amount: 230
  });
});

test('利用不可・メンテナンス等は対象外', () => {
  assert.strictEqual(parseDebitMail('【デビットカード】ご利用不可のお知らせ(ドコモSMTBネット銀行)', USE).kind, 'ignore');
  assert.strictEqual(parseDebitMail('【デビットカード】Wallet登録完了のお知らせ', '').kind, 'ignore');
});

test('本文が読めないときは理由つきで対象外', () => {
  const tx = parseDebitMail('【デビットカード】ご利用のお知らせ', '本文なし');
  assert.strictEqual(tx.kind, 'ignore');
  assert.ok(tx.reason);
});

test('店名が空でも読み取れる', () => {
  const tx = parseDebitMail('【デビットカード】ご利用のお知らせ', USE.replace('LAWSON', ''));
  assert.strictEqual(tx.store, '');
  assert.strictEqual(tx.amount, 459);
});

test('ルールは大文字小文字・半角カナを吸収して上から順に当たる', () => {
  const rules = [
    { keyword: 'lawson', category: '食費', genre: '食料品' },
    { keyword: 'カ ハチヨウ', category: '食費', genre: 'カフェ' },
    { keyword: 'LAW', category: 'その他', genre: 'その他' }
  ];
  assert.strictEqual(matchRule('LAWSON', rules).genre, '食料品');
  assert.strictEqual(matchRule('ｶ ﾊﾁﾖｳ', rules).genre, 'カフェ');
  assert.strictEqual(matchRule('UNKNOWN SHOP', rules), null);
  assert.strictEqual(matchRule('', rules), null);
  assert.strictEqual(normalizeText('ﾒﾙｶﾘ'), 'メルカリ');
});
