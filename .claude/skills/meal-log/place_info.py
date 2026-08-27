# -*- coding: utf-8 -*-
"""店の URL (Google マップ / 食べログ / その他) から店名を取り出す。

使い方:
    python place_info.py https://maps.app.goo.gl/xxxx
    python place_info.py https://tabelog.com/tokyo/A1301/.../

出力: {"ok":true,"name":"店名","url":"<正規化したURL>","source":"maps|tabelog|title"}
名前が取れないときは name を空にして返すので、その場合はユーザーに聞くこと。
"""
import json
import re
import sys
import urllib.parse
import urllib.request

UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/122.0 Safari/537.36')


def fetch(url):
    """最終 URL と HTML(先頭のみ) を返す。"""
    req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept-Language': 'ja'})
    with urllib.request.urlopen(req, timeout=30) as res:
        return res.geturl(), res.read(200000).decode('utf-8', 'replace')


def name_from_maps_url(url):
    m = re.search(r'/maps/place/([^/@?]+)', url)
    if not m:
        return ''
    name = urllib.parse.unquote_plus(m.group(1))
    return strip_address(name.strip())


def strip_address(name):
    """「〒335-0021 埼玉県… もつ焼き琥羽 北戸田店」から店名だけ取り出す。

    共有リンクの place 部分は住所つきの表記になることがあるので、
    郵便番号と都道府県から始まる住所を落とす。落とし切って空になるなら元のまま返す。
    """
    if not name.startswith('〒'):
        return name
    parts = [p for p in name.split(' ') if p]
    rest = parts[1:]
    if rest and re.search(r'[都道府県]', rest[0]):
        rest = rest[1:]
    return ' '.join(rest).strip() or name


def name_from_title(html):
    m = re.search(r'<title[^>]*>(.*?)</title>', html, re.S | re.I)
    if not m:
        return ''
    title = re.sub(r'\s+', ' ', m.group(1)).strip()
    # 「店名 - 場所/ジャンル | 食べログ」「店名 - Google マップ」などの装飾を落とす
    title = re.split(r'\s*[-|｜]\s*', title)[0]
    title = re.sub(r'\s*[（(]食べログ[）)]\s*$', '', title)
    return title.strip()


def main():
    if len(sys.argv) < 2:
        raise SystemExit('usage: place_info.py <url>')
    given = sys.argv[1]
    try:
        final_url, html = fetch(given)
    except Exception as err:  # 取得できなくても URL だけは記録できるようにする
        # Windows のコンソールは cp932 なので日本語はエスケープしたまま出す
        sys.stdout.write(json.dumps({'ok': False, 'name': '', 'url': given, 'error': str(err)}))
        return

    source = 'title'
    name = ''
    if 'google.' in final_url and '/maps/' in final_url:
        name = name_from_maps_url(final_url)
        source = 'maps'
    if not name:
        name = name_from_title(html)
        if 'tabelog.com' in final_url:
            source = 'tabelog'

    # 短縮 URL は展開後の長い URL より、渡された短い方が扱いやすい
    url = given if 'maps.app.goo.gl' in given or 'goo.gl' in given else final_url
    sys.stdout.write(json.dumps({'ok': True, 'name': name, 'url': url, 'source': source}))


if __name__ == '__main__':
    main()
