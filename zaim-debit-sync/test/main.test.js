const test = require('node:test');
const assert = require('node:assert');
const parser = require('../Parser.js');
global.matchRule = parser.matchRule;
const { buildRow_, COL } = require('../Main.js');

const rules = [{ keyword: 'LAWSON', category: '食費', displayName: 'ローソン' }];
const tx = (o) => Object.assign({ kind: 'use', approval: '774068', date: '2026-11-09', store: 'LAWSON', currency: 'JPY', amount: 459 }, o);

test('利用はルールどおりの店名・カテゴリで 1 行になる', () => {
  const r = buildRow_(tx(), rules, []);
  assert.deepStrictEqual(r, ['2026-11-09', '2026-11', 'ローソン', 459, '食費', '利用', '774068', 'LAWSON', '', '']);
});

test('ルールに無い店は未分類', () => {
  const r = buildRow_(tx({ store: 'UNKNOWN' }), rules, []);
  assert.strictEqual(r[COL.category], '未分類');
  assert.strictEqual(r[COL.store], 'UNKNOWN');
});

test('返金はマイナスの行になり、元の利用の店名・カテゴリを引き継ぐ', () => {
  const prior = buildRow_(tx({ approval: '021928', amount: 14180 }), rules, []);
  // シートから読み戻すと承認番号は文字列のまま、という前提で照合
  const r = buildRow_(tx({ kind: 'refund', approval: '021928', amount: 14180, store: '' }), rules, [prior]);
  assert.strictEqual(r[COL.amount], -14180);
  assert.strictEqual(r[COL.kind], '返金');
  assert.strictEqual(r[COL.category], '食費');
});

test('売上確定: 記録済みで同額なら何も書かない', () => {
  const prior = buildRow_(tx(), rules, []);
  assert.strictEqual(buildRow_(tx({ kind: 'settle' }), rules, [prior]), null);
});

test('売上確定: 金額が違えば要確認、未記録なら利用として書く', () => {
  const prior = buildRow_(tx(), rules, []);
  const diff = buildRow_(tx({ kind: 'settle', amount: 500 }), rules, [prior]);
  assert.strictEqual(diff[COL.kind], '要確認');
  assert.strictEqual(diff[COL.amount], 0);
  const fresh = buildRow_(tx({ kind: 'settle', approval: '000000' }), rules, [prior]);
  assert.strictEqual(fresh[COL.kind], '利用（売上確定）');
  assert.strictEqual(fresh[COL.amount], 459);
});

test('外貨は金額を入れず要確認', () => {
  const r = buildRow_(tx({ kind: 'settle', approval: '000000', currency: 'USD', amount: 10 }), rules, []);
  assert.strictEqual(r[COL.kind], '要確認');
  assert.strictEqual(r[COL.amount], 0);
});
