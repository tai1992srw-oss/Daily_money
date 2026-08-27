# -*- coding: utf-8 -*-
"""店の URL から店名を取り出す。URL の代わりに店名を渡すこともできる。

使い方:
    python place_info.py https://maps.app.goo.gl/xxxx
    python place_info.py https://tabelog.com/tokyo/A1301/.../
    python place_info.py "もつ焼き琥羽 北戸田店"   # 店名だけのとき

出力: {"ok":true,"name":"店名","area":"埼玉県戸田市","url":"<URL>","source":"maps|tabelog|title|search"}
area はアプリの「お店」タブで地域ごとにまとめるのに使う。取れなければ空。
URL でなく店名を渡した場合は Google マップの検索 URL を組み立てて source=search で返す
（アプリのチップをタップするとその名前でマップが開く）。より正確なリンクを残したいときは
先に WebSearch で店のページを探し、その URL をこのスクリプトに渡すこと。
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


def place_segment(url):
    """Google マップ URL の /maps/place/<ここ> をデコードして返す。住所つきのこともある。"""
    m = re.search(r'/maps/place/([^/@?]+)', url)
    return urllib.parse.unquote_plus(m.group(1)).strip() if m else ''


def name_from_maps_url(url):
    return strip_address(place_segment(url))


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


# 食べログの URL に入る都道府県スラッグ（/saitama/A1102/... の1つ目）
TABELOG_PREF = {
    'hokkaido': '北海道', 'aomori': '青森県', 'iwate': '岩手県', 'miyagi': '宮城県',
    'akita': '秋田県', 'yamagata': '山形県', 'fukushima': '福島県', 'ibaraki': '茨城県',
    'tochigi': '栃木県', 'gunma': '群馬県', 'saitama': '埼玉県', 'chiba': '千葉県',
    'tokyo': '東京都', 'kanagawa': '神奈川県', 'niigata': '新潟県', 'toyama': '富山県',
    'ishikawa': '石川県', 'fukui': '福井県', 'yamanashi': '山梨県', 'nagano': '長野県',
    'gifu': '岐阜県', 'shizuoka': '静岡県', 'aichi': '愛知県', 'mie': '三重県',
    'shiga': '滋賀県', 'kyoto': '京都府', 'osaka': '大阪府', 'hyogo': '兵庫県',
    'nara': '奈良県', 'wakayama': '和歌山県', 'tottori': '鳥取県', 'shimane': '島根県',
    'okayama': '岡山県', 'hiroshima': '広島県', 'yamaguchi': '山口県', 'tokushima': '徳島県',
    'kagawa': '香川県', 'ehime': '愛媛県', 'kochi': '高知県', 'fukuoka': '福岡県',
    'saga': '佐賀県', 'nagasaki': '長崎県', 'kumamoto': '熊本県', 'oita': '大分県',
    'miyazaki': '宮崎県', 'kagoshima': '鹿児島県', 'okinawa': '沖縄県',
}

PREF_RE = r'(北海道|東京都|京都府|大阪府|[^\s]{2,3}?県)'


def area_from_address(text):
    """住所つきの表記から「都道府県+市区町村」を取り出す。取れなければ都道府県だけ。"""
    m = re.search(PREF_RE + r'([^\s0-9０-９]{1,8}?[市区町村])', text)
    if m:
        return m.group(1) + m.group(2)
    m = re.search(PREF_RE, text)
    return m.group(1) if m else ''


def area_from_tabelog(url, html):
    """食べログの URL のスラッグ（都道府県）とタイトルのエリア名（駅名など）を組み合わせる。"""
    m = re.search(r'tabelog\.com/([a-z]+)/', url)
    pref = TABELOG_PREF.get(m.group(1), '') if m else ''
    local = ''
    t = re.search(r'<title[^>]*>(.*?)</title>', html, re.S | re.I)
    if t:
        # 「店名 - 北戸田/居酒屋 | 食べログ」の「北戸田」を拾う
        parts = re.split(r'\s*[-|｜]\s*', re.sub(r'\s+', ' ', t.group(1)))
        if len(parts) > 1:
            local = parts[1].split('/')[0].strip()
    if local and local != pref:
        return pref + local
    return pref


def name_from_title(html):
    m = re.search(r'<title[^>]*>(.*?)</title>', html, re.S | re.I)
    if not m:
        return ''
    title = re.sub(r'\s+', ' ', m.group(1)).strip()
    # 「店名 - 場所/ジャンル | 食べログ」「店名 - Google マップ」などの装飾を落とす
    title = re.split(r'\s*[-|｜]\s*', title)[0]
    title = re.sub(r'\s*[（(]食べログ[）)]\s*$', '', title)
    # 食べログの「〜のご予約」「（もつやき こはね）」のような装飾を落とす
    title = re.sub(r'\s*[（(][ぁ-んァ-ヶー・\s]+[）)]', '', title)
    title = re.sub(r'\s*の(ご)?(ネット)?予約\s*$', '', title)
    return title.strip()


def search_url(name):
    """Google マップ URLs API の検索リンク。スマホではマップアプリがそのまま開く。"""
    return 'https://www.google.com/maps/search/?api=1&query=' + urllib.parse.quote(name)


def main():
    if len(sys.argv) < 2:
        raise SystemExit('usage: place_info.py <url|店名>')
    given = sys.argv[1].strip()

    # URL でなければ店名として扱い、マップの検索リンクを組み立てて返す
    # （エリアは分からないので、ユーザーが地域を言っていればスキル側で place_area に入れる）
    if not re.match(r'https?://', given):
        sys.stdout.write(json.dumps(
            {'ok': True, 'name': given, 'area': '', 'url': search_url(given), 'source': 'search'}))
        return

    try:
        final_url, html = fetch(given)
    except Exception as err:  # 取得できなくても URL だけは記録できるようにする
        # Windows のコンソールは cp932 なので日本語はエスケープしたまま出す
        sys.stdout.write(json.dumps(
            {'ok': False, 'name': '', 'area': '', 'url': given, 'error': str(err)}))
        return

    source = 'title'
    name = ''
    area = ''
    if 'google.' in final_url and '/maps/' in final_url:
        segment = place_segment(final_url)
        name = strip_address(segment)
        area = area_from_address(segment)
        source = 'maps'
    if not name:
        name = name_from_title(html)
        if 'tabelog.com' in final_url:
            source = 'tabelog'
    if not area and 'tabelog.com' in final_url:
        area = area_from_tabelog(final_url, html)

    # 短縮 URL は展開後の長い URL より、渡された短い方が扱いやすい
    url = given if 'maps.app.goo.gl' in given or 'goo.gl' in given else final_url
    sys.stdout.write(json.dumps(
        {'ok': True, 'name': name, 'area': area, 'url': url, 'source': source}))


if __name__ == '__main__':
    main()
