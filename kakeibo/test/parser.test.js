const test = require('node:test');
const assert = require('node:assert');
const { parseBankMail, matchRule, normalizeText } = require('../Parser.js');
const { sampleMails } = require('../dev/memory.js');

const mails = Object.fromEntries(sampleMails('2026-11').map((m) => [m.id, m]));
const parse = (id) => parseBankMail(mails[id].subject, mails[id].body);

test('デビット利用: 店名・金額・日時・承認番号を読む', () => {
  assert.deepStrictEqual(parse('m1'), {
    kind: 'use', approval: '774068', date: '2026-11-03', time: '05:56', store: 'LAWSON', currency: 'JPY', amount: 459
  });
});

test('デビット返金: カンマ入りの金額も読める', () => {
  const tx = parse('m4');
  assert.strictEqual(tx.kind, 'refund');
  assert.strictEqual(tx.amount, 14180);
});

test('デビット売上確定: 利用日・利用金額から読む（時刻なし）', () => {
  const tx = parse('m6');
  assert.strictEqual(tx.kind, 'settle');
  assert.strictEqual(tx.date, '2026-11-06');
  assert.strictEqual(tx.time, '');
  assert.strictEqual(tx.amount, 1888);
  assert.strictEqual(tx.store, 'ﾒﾙｶﾘ');
});

test('利用不可・メンテナンスは対象外', () => {
  assert.strictEqual(parse('m5').kind, 'ignore');
  assert.strictEqual(parse('m9').kind, 'ignore');
});

test('出金のお知らせ: サービス名と和文の日時を読む（金額なし）', () => {
  assert.deepStrictEqual(parse('m7'), { kind: 'withdraw', date: '2026-11-06', time: '10:05', name: 'PayPay' });
});

test('午後の時刻と振込受付', () => {
  const tx = parseBankMail('出金のお知らせ', '2026年10月4日午後10時48分に出金をいたしました。\nサービス名：メルペイ');
  assert.strictEqual(tx.time, '22:48');
  const noon = parseBankMail('出金のお知らせ', '2026年10月4日午後12時5分に出金\nサービス名：ことら送金');
  assert.strictEqual(noon.time, '12:05');
  const tr = parseBankMail('振込受付のお知らせ', '2026年11月12日に振込依頼を受付けました。\n・受付番号：2611000002\n');
  assert.deepStrictEqual(tr, { kind: 'transfer', date: '2026-11-12', time: '', name: '振込', ref: '2611000002' });
});

test('振込入金のお知らせ', () => {
  assert.deepStrictEqual(parse('m8'), { kind: 'deposit', date: '2026-11-01', time: '', name: '振込入金' });
});

test('本文が読めないデビット通知は理由つきで対象外', () => {
  const tx = parseBankMail('【デビットカード】ご利用のお知らせ', '本文なし');
  assert.strictEqual(tx.kind, 'ignore');
  assert.ok(tx.reason);
});

test('ルールは大文字小文字・半角カナを吸収して上から順に当たる', () => {
  const rules = [
    { keyword: 'lawson', category: '食費' },
    { keyword: 'カ ハチヨウ', category: '交際費' },
    { keyword: 'LAW', category: 'その他' }
  ];
  assert.strictEqual(matchRule('LAWSON', rules).category, '食費');
  assert.strictEqual(matchRule('ｶ ﾊﾁﾖｳ', rules).category, '交際費');
  assert.strictEqual(matchRule('UNKNOWN SHOP', rules), null);
  assert.strictEqual(normalizeText('ﾒﾙｶﾘ'), 'メルカリ');
});
