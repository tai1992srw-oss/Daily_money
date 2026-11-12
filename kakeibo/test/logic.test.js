const test = require('node:test');
const assert = require('node:assert');
const { handleApi } = require('../Logic.js');
const { memoryStore, memoryMail, sampleMails } = require('../dev/memory.js');

function setup(extraMails = []) {
  const store = memoryStore();
  const mail = memoryMail(sampleMails('2026-11').concat(extraMails));
  const api = (action, payload) => handleApi(store, mail, action, payload, '2026-11-12T00:00:00Z');
  return { api };
}
const byMsg = (state, id) => state.entries.find((e) => e.messageId === id);

test('メール取り込み: デビットは店名・カテゴリ付き、金額なしのメールは入力待ちになる', () => {
  const { api } = setup();
  const s = api('sync', { days: 3 });
  assert.strictEqual(s.added, 7);               // 利用不可とメンテナンスの 2 通は入らない
  assert.deepStrictEqual(
    [byMsg(s, 'm1').name, byMsg(s, 'm1').category, byMsg(s, 'm1').amount, byMsg(s, 'm1').status],
    ['ローソン', '食費', 459, 'ok']);
  assert.strictEqual(byMsg(s, 'm2').category, '未分類');
  assert.strictEqual(byMsg(s, 'm2').name, 'Japan Bridge Center');
  // 返金はマイナスの支出で、元の利用の店名・カテゴリを引き継ぐ
  assert.strictEqual(byMsg(s, 'm4').amount, -14180);
  assert.strictEqual(byMsg(s, 'm4').category, '交通');
  // 承認なしの売上確定は利用として記録
  assert.strictEqual(byMsg(s, 'm6').amount, 1888);
  // 金額が書かれていないメール
  assert.deepStrictEqual([byMsg(s, 'm7').status, byMsg(s, 'm7').amount, byMsg(s, 'm7').category, byMsg(s, 'm7').type],
    ['pending', '', '振替・チャージ', 'expense']);
  assert.deepStrictEqual([byMsg(s, 'm8').status, byMsg(s, 'm8').type, byMsg(s, 'm8').category], ['pending', 'income', '収入']);
  // 残高: 459 + 440 + 14180 - 14180 + 1888 の支出
  assert.strictEqual(s.balance, -(459 + 440 + 1888));
  assert.strictEqual(s.lastSync, '2026-11-12T00:00:00Z');
});

test('同じメールを二度取り込まない', () => {
  const { api } = setup();
  api('sync', { days: 3 });
  const again = api('sync', { days: 3 });
  assert.strictEqual(again.added, 0);
  assert.strictEqual(again.entries.length, 7);
});

test('入力待ちに金額を入れると残高に反映される', () => {
  const { api } = setup();
  let s = api('sync', {});
  const dep = byMsg(s, 'm8');
  s = api('saveEntry', { entry: { id: dep.id, amount: '250,000', date: dep.date } });
  assert.strictEqual(byMsg(s, 'm8').status, 'ok');
  assert.strictEqual(s.balance, 250000 - (459 + 440 + 1888));
});

test('カテゴリを直して「今後も同じ」にすると、ルールができて同じ店の記録にも当たる', () => {
  const second = sampleMails('2026-11')[1];
  const extra = Object.assign({}, second, { id: 'm2b', body: second.body.replace('214586', '521888') });
  const { api } = setup([extra]);
  let s = api('sync', {});
  const jb = byMsg(s, 'm2');
  s = api('saveEntry', { entry: { id: jb.id, category: '食費', name: '自販機', date: jb.date, amount: jb.amount }, learn: true });
  assert.deepStrictEqual([byMsg(s, 'm2b').category, byMsg(s, 'm2b').name], ['食費', '自販機']);
  assert.deepStrictEqual(s.rules[0], { keyword: 'Japan Bridge Center', category: '食費', displayName: '自販機' });
});

test('売上確定で金額が変わったら、利用時の記録を確定金額に直す', () => {
  const settle = {
    id: 'm10', subject: '【デビットカード】引落しまたは入金処理のお知らせ(ドコモSMTBネット銀行)',
    body: '承認番号 ： 774068\n利用日 ： 2026/11/03\n利用加盟店 ： ﾛ-ｿﾝ\n利用通貨 ： JPY\n利用金額 ： 500.00\n'
  };
  const { api } = setup([settle]);
  const s = api('sync', {});
  assert.strictEqual(byMsg(s, 'm10'), undefined);
  assert.strictEqual(byMsg(s, 'm1').amount, 500);
  assert.match(byMsg(s, 'm1').memo, /利用時 459 円/);
  // 何度取り込んでも結果は同じ
  assert.strictEqual(api('sync', {}).entries.length, s.entries.length);
});

test('手入力: 口座払いは残高に効き、現金は効かない', () => {
  const { api } = setup();
  let s = api('saveEntry', { entry: { type: 'income', amount: 10000, date: '2026-11-01', category: '収入' } });
  s = api('saveEntry', { entry: { type: 'expense', amount: 3000, date: '2026-11-02', category: '食費', name: '昼ご飯' } });
  s = api('saveEntry', { entry: { type: 'expense', amount: 800, date: '2026-11-02', category: '食費', method: 'その他' } });
  assert.strictEqual(s.balance, 7000);
  assert.strictEqual(s.entries.length, 3);
  assert.strictEqual(s.entries[1].source, 'manual');
});

test('残高を合わせると差額が残高調整として入る', () => {
  const { api } = setup();
  api('sync', {});
  const s = api('setBalance', { actual: '123,456', today: '2026-11-12' });
  assert.strictEqual(s.balance, 123456);
  const adj = s.entries[s.entries.length - 1];
  assert.deepStrictEqual([adj.category, adj.type], ['残高調整', 'income']);
});

test('削除と入力エラー', () => {
  const { api } = setup();
  const s = api('saveEntry', { entry: { amount: 100, date: '2026-11-01' } });
  assert.strictEqual(api('deleteEntry', { id: s.entries[0].id }).entries.length, 0);
  assert.throws(() => api('saveEntry', { entry: { amount: 'abc', date: '2026-11-01' } }), /金額/);
  assert.throws(() => api('saveEntry', { entry: { amount: 100, date: '' } }), /日付/);
  assert.throws(() => api('deleteEntry', { id: 'nope' }), /見つかりません/);
});

test('ルール保存で未分類の記録に当て直す', () => {
  const { api } = setup();
  let s = api('sync', {});
  s = api('saveRules', { rules: s.rules.concat([{ keyword: 'メルカリ', category: 'その他', displayName: 'メルカリ' }]) });
  assert.deepStrictEqual([byMsg(s, 'm6').category, byMsg(s, 'm6').name], ['その他', 'メルカリ']);
});
