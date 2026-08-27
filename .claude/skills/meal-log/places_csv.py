# -*- coding: utf-8 -*-
"""行った店の一覧を Google マイマップ取り込み用の CSV に書き出す。

    python places_csv.py [出力先.csv]      # 省略時は places.csv

マイマップ（https://mymaps.google.com）で「新しい地図 → インポート」からこの CSV を
アップロードし、
  - 目印の場所を選ぶ列 → 「場所」
  - 目印のタイトルにする列 → 「店名」
を指定すると、行った店がピンで並んだ地図になる（無料。1レイヤ2000行まで）。

「場所」列は「店名 エリア」を入れてあり、マイマップ側でジオコーディングされる。
エリアが空の店は店名だけで検索されるので、ズレたら地図上で直接ピンを動かす。
"""
import csv
import io
import json
import os
import sys
import urllib.parse
import urllib.request

# .claude/skills/meal-log/ から3つ上がリポジトリのルート
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
CONFIG = os.path.join(ROOT, '.claude', 'diet-bot.local.json')

HEADER = ['店名', '場所', 'エリア', '訪問回数', '食事件数', '合計kcal', '初回', '最終', 'URL']


def fetch_places():
    with io.open(CONFIG, encoding='utf-8') as f:
        cfg = json.load(f)
    url = cfg['api_url'] + '?' + urllib.parse.urlencode(
        {'token': cfg['token'], 'action': 'places'})
    with urllib.request.urlopen(url, timeout=60) as res:
        data = json.loads(res.read().decode('utf-8'))
    if not data.get('ok'):
        raise SystemExit('API エラー: %s' % data.get('error'))
    return data.get('places', [])


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else 'places.csv'
    places = fetch_places()

    # Excel で開く前提なので BOM つき UTF-8。マイマップもこれで文字化けしない
    with io.open(out, 'w', encoding='utf-8-sig', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(HEADER)
        for p in places:
            name = p.get('name', '')
            area = p.get('area', '')
            writer.writerow([
                name,
                (name + ' ' + area).strip(),
                area,
                p.get('visits', 0),
                p.get('meals', 0),
                p.get('total_kcal', 0),
                p.get('first_date', ''),
                p.get('last_date', ''),
                p.get('url', ''),
            ])

    sys.stdout.write(json.dumps({'ok': True, 'file': out, 'places': len(places)}))


if __name__ == '__main__':
    main()
