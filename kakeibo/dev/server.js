/** 手元確認用: 画面（index.html）を、メモリ上のデータと見本メールで動かす。 node dev/server.js */
const http = require('http');
const fs = require('fs');
const path = require('path');
const { handleApi } = require('../Logic.js');
const { memoryStore, memoryMail, sampleMails } = require('./memory.js');

const store = memoryStore();
const now = new Date();
const ym = now.getFullYear() + '-' + ('0' + (now.getMonth() + 1)).slice(-2);
const mail = memoryMail(sampleMails(ym));

http.createServer((req, res) => {
  if (req.method === 'POST' && req.url === '/api') {
    let body = '';
    req.on('data', (c) => { body += c; });
    req.on('end', () => {
      res.setHeader('Content-Type', 'application/json');
      try {
        const { action, payload } = JSON.parse(body);
        res.end(JSON.stringify(handleApi(store, mail, action, payload)));
      } catch (err) {
        res.end(JSON.stringify({ error: err.message }));
      }
    });
    return;
  }
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.end(fs.readFileSync(path.join(__dirname, '..', 'index.html')));
}).listen(process.env.PORT || 8787, () => console.log('http://localhost:' + (process.env.PORT || 8787)));
