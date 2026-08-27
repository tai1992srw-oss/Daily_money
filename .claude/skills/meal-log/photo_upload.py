# -*- coding: utf-8 -*-
"""チャットに貼られた食事写真を GAS 経由で Drive に保存し、表示用 URL を返す。

使い方:
    python photo_upload.py                 # 会話に貼られた最新の画像を使う
    python photo_upload.py path/to/img.jpg # ファイルを指定して使う

チャットに貼った画像はファイルとして残らないが、Claude Code の会話ログ
(~/.claude/projects/<cwd をスラッシュ変換した名前>/<session>.jsonl) に
base64 で保存されているので、そこから最後のユーザー画像を取り出す。

設定 (.claude/diet-bot.local.json) の api_url / token を読む。
結果は JSON を stdout に出す: {"ok":true,"url":"...","id":"...","bytes":123456}
"""
import base64
import glob
import io
import json
import os
import sys
import urllib.request

# .claude/skills/meal-log/ から3つ上がリポジトリのルート
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
CONFIG = os.path.join(ROOT, '.claude', 'diet-bot.local.json')
MAX_BYTES = 12 * 1024 * 1024  # GAS の POST 上限に対する安全域


def load_config():
    with io.open(CONFIG, encoding='utf-8') as f:
        cfg = json.load(f)
    if not cfg.get('api_url') or not cfg.get('token'):
        raise SystemExit('api_url / token が %s にありません' % CONFIG)
    return cfg


def transcript_dir():
    """カレントディレクトリに対応する会話ログのディレクトリ。"""
    slug = os.path.abspath(os.getcwd()).replace(':', '-').replace('\\', '-').replace('/', '-')
    slug = slug.rstrip('-')
    return os.path.join(os.path.expanduser('~'), '.claude', 'projects', slug)


def latest_pasted_image():
    """会話ログから、ユーザーが最後に貼った画像 (data, mime) を取り出す。"""
    files = sorted(glob.glob(os.path.join(transcript_dir(), '*.jsonl')), key=os.path.getmtime)
    if not files:
        raise SystemExit('会話ログが見つかりません: %s' % transcript_dir())

    found = None
    for path in reversed(files[-3:]):  # 直近のセッションだけ見る
        for line in io.open(path, encoding='utf-8'):
            if '"image"' not in line:
                continue
            try:
                entry = json.loads(line)
            except ValueError:
                continue
            # ツール結果のスクリーンショット等はユーザーが貼った写真ではないので除く
            if entry.get('type') != 'user' or entry.get('toolUseResult') is not None:
                continue
            content = entry.get('message', {}).get('content')
            if not isinstance(content, list):
                continue
            for block in content:
                if not isinstance(block, dict) or block.get('type') != 'image':
                    continue
                src = block.get('source', {})
                if src.get('type') == 'base64' and src.get('data'):
                    found = (src['data'], src.get('media_type', 'image/jpeg'))
        if found:
            return found
    raise SystemExit('会話に貼られた画像が見つかりません。ファイルパスを指定してください')


def from_file(path):
    with open(path, 'rb') as f:
        raw = f.read()
    ext = os.path.splitext(path)[1].lower()
    mime = {'.png': 'image/png', '.webp': 'image/webp'}.get(ext, 'image/jpeg')
    return base64.b64encode(raw).decode('ascii'), mime


def post(api_url, payload):
    """GAS は 302 を返すのでリダイレクト先へは GET で追う (POST 時点で処理は完了)。"""
    body = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(
        api_url, data=body, headers={'Content-Type': 'application/json; charset=utf-8'}
    )
    with urllib.request.urlopen(req, timeout=180) as res:
        return json.loads(res.read().decode('utf-8'))


def main():
    cfg = load_config()
    if len(sys.argv) > 1:
        data, mime = from_file(sys.argv[1])
        filename = os.path.basename(sys.argv[1])
    else:
        data, mime = latest_pasted_image()
        filename = ''

    size = len(data) * 3 // 4
    if size > MAX_BYTES:
        raise SystemExit('画像が大きすぎます (%.1f MB)。縮小してから渡してください' % (size / 1048576.0))

    result = post(cfg['api_url'], {
        'token': cfg['token'],
        'action': 'uploadPhoto',
        'data': data,
        'mime': mime,
        'filename': filename,
    })
    result['bytes'] = size
    sys.stdout.write(json.dumps(result))  # cp932 コンソール対策で日本語はエスケープのまま


if __name__ == '__main__':
    main()
