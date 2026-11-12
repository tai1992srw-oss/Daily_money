/** テストと手元確認用の、メモリ上の保存先とメール取得元。 */
const { SEED_RULES } = require('../Logic.js');

function memoryStore() {
  const data = { entries: [], rules: SEED_RULES.map((r) => Object.assign({}, r)), settings: {} };
  const copy = (x) => JSON.parse(JSON.stringify(x));
  return {
    load: () => copy(data),
    saveEntries: (entries) => { data.entries = copy(entries); },
    saveRules: (rules) => { data.rules = copy(rules); },
    saveSetting: (k, v) => { data.settings[k] = v; }
  };
}

function memoryMail(messages) {
  return { list: (days, isDone) => messages.filter((m) => !isDone(m.id)) };
}

const debit = (id, subjectKind, approval, when, store, amount, opt = {}) => ({
  id,
  subject: `【デビットカード】${subjectKind}(ドコモSMTBネット銀行)`,
  body: ` 山田　太郎 さま\n\n【取引情報】\n承認番号 ： ${approval}\n${opt.dateLabel || '利用日時'} ： ${when}\n` +
    `利用加盟店 ： ${store}\n${opt.settle ? '利用通貨' : '引落通貨'} ： ${opt.currency || 'JPY'}\n` +
    `${opt.settle ? '利用金額' : '引落金額'} ： ${amount}\n`
});

/** 実際のメールと同じ書式の見本（名前と金額は架空）。 */
function sampleMails(ym) {
  return [
    debit('m1', 'ご利用のお知らせ', '774068', `${ym.replace('-', '/')}/03 05:56:51`, 'LAWSON', '459.00'),
    debit('m2', 'ご利用のお知らせ', '214586', `${ym.replace('-', '/')}/03 13:12:03`, 'Japan Bridge Center', '440.00'),
    debit('m3', 'ご利用のお知らせ', '021928', `${ym.replace('-', '/')}/04 19:06:31`, 'EAST JAPAN RAILWAY CO.', '14,180.00'),
    debit('m4', 'ご返金のお知らせ', '021928', `${ym.replace('-', '/')}/04 19:06:31`, 'EAST JAPAN RAILWAY CO.', '14,180.00'),
    debit('m5', 'ご利用不可のお知らせ', '', `${ym.replace('-', '/')}/05 14:24:38`, 'SHIBUYA SAKURA STAGE', '3400.00'),
    debit('m6', '引落しまたは入金処理のお知らせ', '000000', `${ym.replace('-', '/')}/06`, 'ﾒﾙｶﾘ', '1888.00',
      { settle: true, dateLabel: '利用日' }),
    { id: 'm7', subject: '出金のお知らせ',
      body: `出金のお知らせ\n山田 太郎さま\n以下のサービスにつきまして、${ym.slice(0, 4)}年${Number(ym.slice(5))}月6日午前10時5分に` +
        'お客さまの普通預金口座より出金をいたしました。\nサービス名：ＰａｙＰａｙ\n' },
    { id: 'm8', subject: '振込入金のお知らせ',
      body: `振込入金のお知らせ\n山田 太郎 さま\n${ym.slice(0, 4)}年${Number(ym.slice(5))}月1日に、お客さまの代表口座円普通預金への振込入金がありました。\n` },
    { id: 'm9', subject: '臨時システムメンテナンスのお知らせ', body: 'システムメンテナンスのため…' }
  ];
}

module.exports = { memoryStore, memoryMail, sampleMails };
