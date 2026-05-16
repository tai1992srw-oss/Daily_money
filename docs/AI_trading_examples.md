# AI × 短期投資・FX 成功事例集

短期トレード（デイトレ・スキャル・スイング）と FX を中心に、AI／機械学習を活用した成功事例を国内外から収集した資料。
実運用での収益例、デモトレード、バックテスト、学術論文の結果を区別して整理している。

最終更新: 2026-05-16

---

## 0. サマリー

| 区分 | 代表事例 | 成果（概数） | 種別 |
|---|---|---|---|
| 海外HF | Renaissance Medallion | 年率66%（手数料前, 1988-) | 実運用 |
| 海外HF | Two Sigma Spectrum / Absolute Return Enhanced | +10.9% / +14.3%（2024年） | 実運用 |
| 海外MM | XTX Markets | 売上 $5.3B（2025, 前年比+44%） | 実運用 |
| 海外MM | Citadel Securities | 純取引収益 $12B（2025, +25% YoY） | 実運用 |
| AI bot | Tickeron AI Trading Agent (NVDA) | 年率204%・勝率85.1% | 実運用ボット指標 |
| 個人 | Joe Tay (Medium) | 年率 43.8% APR | 実運用（小額） |
| 個人 | ImbueDesk LSTM (BTC/ETH) | ETHUSDT 66,941%（15分足アンサンブル） | バックテスト |
| 学術 | Deep RL + 補助タスク (EUR/USD) | -25%→+14.8%、+2.1%→+42.2% | バックテスト |
| 学術 | Q-Learning NN (EUR/USD, 2010-17) | 年率 16.3 ± 2% | バックテスト |
| 国内HF | Magne-Max Capital Management | Yahoo!Japan系、AI/強化学習で公私募運用 | 実運用 |
| 国内HF | シンプレクス・アセット・マネジメント | 国内独立系最大級 | 実運用 |
| 国内個人 | 50代兼業（モメンタムAI併用） | 累積1億円超 | 実運用 |

短期帯では「人間が見つけられない微小なエッジを大量回数で取りに行く」アプローチが勝ち筋。
派手な単発リターンより、勝率50〜55%×高頻度×低コストの組み合わせが業界の本流。

---

## 1. 海外：機関投資家の実運用事例

### 1.1 Renaissance Technologies — Medallion Fund（米）
- **戦略**：完全自動の超短期統計的裁定。1日最大30万トレード規模。1トレードあたりの利益は 0.01〜0.05%、レバ 10〜20倍で増幅。
- **成績**：1988年以降、手数料前 年率約66%、手数料後 年率約39%。1994〜2014年央の平均は手数料前 71.8%。1988-2022 累積で純額90,129倍。
- **AI要素**：1990年代初期に IBM の音声認識チーム（Brown, Mercer ら）を引き抜き、隠れマルコフ・初期の機械学習を相場時系列に応用。短期予測の機械学習採用は業界最古参。
- **示唆**：成功の核は「予測精度」ではなく、勝率 50.75% を執行・コスト管理で利益化する仕組み全体。短期AIは "モデル単独" では完結しない好例。

### 1.2 Two Sigma（米）
- 2024年 Spectrum +10.9%、Absolute Return Enhanced +14.3%。マルチストラテジー / 短中期がメイン。
- 衛星画像・ニュース・代替データを ML モデルに投入。アルファ生成と執行両面に ML を組み込む。
- 2026年「AI-First」社内指令を出し、フロンティアモデルを全社で日常運用に統合（Operational Alpha）。

### 1.3 Citadel / Citadel Securities（米）
- Citadel Securities は 2025年に純取引収益 $12B（前年比+25%）。マーケットメイク主体だがアルゴ全自動。
- 強化学習で執行・在庫管理を最適化、ミリ秒〜マイクロ秒の意思決定をAIに委任。
- 8〜9割のモデルはバックテストで利益が出ても本番投入前に棄却。**過学習除去のプロセス自体がエッジ**。

### 1.4 XTX Markets（英）
- 2015年創業。"スピード勝負"の従来HFTから一線を画し、機械学習による**予測精度**で勝負。
- GPU 25,000基超（A100×10,000、V100×10,000）、ストレージ 650PB の社内クラスタ。
- 1日 1兆データポイント超を ML で処理。
- 売上：2024年 £2.74B（$3.53B）→ 2025年 $5.3B（+44%）、営業利益 +33%。1日 $250B 規模を取引。

### 1.5 Tickeron AI Trading Bots（米）
- 公開 AI ボット指標で、上位の月次リターンが最大 +171%。
- NVDA 用 AI Trading Agent：**年率換算 204%、勝率 85.1%**。
- レバレッジ ETF・セクター ETF 横断で **勝率 86.6% まで**を提示。
- 注：これは「指標」公表値で、戦略選択・期間・スリッページ前提に依存。市場局面で大きくブレることに留意。

---

## 2. 海外：個人・コミュニティの事例（実運用＋バックテスト）

### 2.1 Joe Tay：失敗から +43.8% APR ボットへ（Medium）
- 数々の戦略がバックテストでは良好でもライブで崩壊。
- AI に **Donchian Channel ブレイクアウト**を提案させ、特徴量とリスクパラメータを LLM 駆動で最適化。
- 2025年8月に実資金投入、**年率換算 43.8% APR** に到達。
- ポイント：「AIに戦略アイデアを提案させる」→「人間がフィルタ／バックテスト検証」→「執行は自動」のハイブリッド。

### 2.2 ImbueDesk LSTM / アンサンブル（BTC・ETH）
- 単体 LSTM で 3年 +720%（BTC, バックテスト）。
- TCN + LSTM + Transformer のアンサンブルで、1023日で BTC +4,750%、ETH +11,270%。
- 15分足での 6モデルアンサンブルが ETHUSDT で **+66,941%**（過適合の典型例として参照価値あり）。
- KNN クラスタリングで 138 銘柄から選別する Freqtrade 戦略で **+8,000% 超**（バックテスト）。
- ※ いずれもバックテスト主体、ライブ実績は限定的。**look-ahead / 生存バイアスのリスク**を著者自身も警告。

### 2.3 ChatGPT を使った個人デイトレード
- HBR（2026年3月）：複数 LLM に銘柄選別を競わせた結果、短期のテーマ捕捉では人間より優位な場面あり、ただし長期では情報の鮮度切れに弱い。
- Alpha Architect：ChatGPT を使ったニュース解釈モメンタム戦略は、S&P500銘柄で**Sharpe・Sortino とも標準モメンタムを上回り、in/out-sample 双方で頑健**、取引コスト後も優位。
- LinkedIn 投稿事例（A. Patel）：個人ピックを ChatGPT との併用で +2.7% アウトパフォーム。

---

## 3. 学術研究：再現可能な短期戦略バックテスト

### 3.1 Deep RL × 補助タスク（EUR/USD, arXiv:2411.01456, 2024）
- PPO ベースの強化学習エージェントに補助タスク（補助損失）を追加。
- 2つのデータセットで **-25.25% → +14.86%** と **+2.12% → +42.22%** に改善。短期帯の DL+RL ありの代表例。

### 3.2 Q-Learning ニューラルネット（Carapuço, Neves ら）
- EUR/USD 2010-2017 の日中足で訓練、10試行平均 **総リターン +114%、年率 16.3 ± 2%**。
- Elsevier Expert Systems with Applications 掲載。学術的に再現性が比較的高い。

### 3.3 Direct Reinforcement Learning（Moody & Saffell）
- USD/GBP 短期 FX で、教師あり手法を上回る成果を 1998 年時点で実証。
- 後続研究の Recurrent RL：年率複利 9.3%、Information Ratio 0.52、モメンタムベンチに対し低ボラ。

### 3.4 LLM 強化モメンタム（複数論文・2024–2025）
- arXiv 2510.26228 ほか：S&P500 + 高頻度ニュース + ChatGPT プロンプトで extracted シグナルを使った短中期モメンタム。**標準モメンタムよりリスク調整後リターンで上回り**、取引コスト・ポートフォリオ制約に頑健。
- 反証研究（arXiv 2505.07078）：20年 100銘柄超のクロスセクションでは LLM 優位が大きく減衰。**短期×ニュース連動**領域に限定される可能性。

### 3.5 強化学習 × マルチエージェント分散（arXiv:2405.19982, 2024）
- 非同期分散 RL で複数 FX ペアを同時最適化。複数通貨・複数戦略並走の枠組みを提示。

---

## 4. 国内：日本の事例

### 4.1 Magne-Max Capital Management（東京）
- 2011 年創業、AI 研究者を中心とした投資顧問会社。
- 2015 年に **Yahoo!Japan グループ入り**し、公募・私募投信の運用助言を開始。
- 機械学習・強化学習・行動ファイナンスを軸に、大規模データを使ったアセットプライシングモデルを開発。短中期のシステマティック運用に強み。

### 4.2 シンプレクス・アセット・マネジメント
- 1999 年創業、日本のヘッジファンドの草分け。
- 金融工学＋システマティック運用で国内独立系最大級。短期裁定／オルタナ戦略に AI / 数理モデルを組み込み。

### 4.3 個人投資家・兼業デイトレーダー（50代）
- 短期モメンタム判定に AI（生成AI＋自前のスクリーニング）を併用。
- **累積で1億円超**のリターン。"AI はトレード前後（仮説生成・振り返り）で使い、エントリーの最終判断は人間" が共通解として紹介されている。

### 4.4 87歳・現役トレーダー（ダイヤモンド報道）
- AI を駆使する株取引で **資産18億円**を維持／更新。生成AI を銘柄スクリーニング、シナリオ生成に使うハイブリッド型。

### 4.5 個人向け AI 短期売買サービス（日経報道）
- 新興 fintech が「ヘッジファンド並みシステム」を個人投資家に供給する動き。
- 証券会社経由で AI 助言を受け、短期売買用シグナルを利用可能に。

### 4.6 LSTM による日経平均予測（複数の実装例）
- Qiita / Zenn / 個人ブログで日経平均の終値・方向を LSTM／Transformer で予測する実験が活発。
- 単純な方向予測の勝率はおおむね **52〜58% 程度**。バニラ LSTM 単独では取引コストを引いた後の優位は薄い、というのが現時点の共通見解。
- 「データサイエンティストが本気で研究したトレード戦略 - 日経平均デイトレ編」（Zenn）はゼロから日中足戦略を AI 化する事例として代表的。

### 4.7 深層強化学習 EA（Note / sayama_ocha）
- MT5 + Python + 深層強化学習を組み合わせた FX 自動売買 EA の開発記事。
- 個人がライブ環境に乗せるための実装テンプレートとして引用例多数。

### 4.8 個人投資家の生成AI 活用調査（IDEATECH）
- 個人投資家の **73.3% が株式投資で生成 AI を活用**、うち **89.2% が AI 分析を元に売買経験あり**。
- 短期売買での利用が中心で、銘柄スクリーニング・ニュース要約・シナリオ生成が主用途。

### 4.9 「Analystant」（AiQ-Index）
- 生成 AI を分析エンジンに据えたアナリスト支援サービス。
- 短中期銘柄選別の **バックテストで継続的にベンチマーク超過**を示すレポートを公開。

---

## 5. 短期帯で「効いている」パターンの整理

実例から抽出した共通点。

1. **超短期・高頻度 × 微小エッジ**：Medallion / Citadel / XTX。勝率は 50〜55% でも、取引数×低コストで勝ち切る。
2. **代替データ × ML**：Two Sigma、Tickeron。価格データだけでなくニュース・センチメント・衛星画像など多変量を ML で統合。
3. **LLM × モメンタム / イベントドリブン**：ChatGPT 系。ニュース解釈の即時性が短期帯と相性◎、長期では効きが落ちる。
4. **強化学習 × 執行最適化**：Citadel、研究系。エントリーよりも執行・在庫管理に RL を使うと安定。
5. **AI は仮説生成、執行は人間 or 機械的ルール**：Joe Tay、国内兼業勢。LLM をアイデアジェネレータとして使い、検証と執行は別レイヤー。

---

## 6. 現実と注意点（成功事例を読むときの前提）

- **過学習（オーバーフィッティング）**：2019年のあるモデルは backtest で勝率94%・年率310%だったが、ライブ初週で -12%。Citadel/Two Sigma も**モデルの 80〜90% は本番投入前に棄却**。
- **取引コストでの蒸発**：手数料ゼロの環境では HFT 的に勝つ RL エージェントも、現実のスプレッド・スリッページを入れると一気に赤字化（複数論文で確認）。
- **市場レジーム変化**：2023-2024 年の低ボラ相場で輝いた AI が、2025 年央の利上げ＋荒い相場で大幅 DD という例が複数。
- **生存バイアス**：公開されている「勝った AI ボット」報告は当然成功例に偏る。ImbueDesk 系の 60,000%+ も再現可能性は限定的。
- **デモ・バックテストとライブの乖離**：Donchian breakout 戦略のように「シンプルだがロバスト」な戦略の方がライブで生き残る傾向。
- **AI を "考えてくれる存在" として過信しない**：成功している国内勢の共通解は「**判断の主体は人、AI は仮説と振り返りの加速装置**」。

---

## 7. このプロジェクトへの示唆（Daily money 向け）

家計・投資管理アプリ目線で使えそうな AI 短期トレードの示唆。

- **シグナル提示はするが、最終判断はユーザー**（国内勝ち組と同じ構造）。
- **「振り返り」エンジン**を AI 化する余地が大きい：日次トレードログを LLM に渡して反省点を可視化。
- **代替データ統合**：日々の家計／資産推移と相場ニュースを横串で見ると、リスク許容度に合わせたアラートが作れる。
- **バックテスト機能を載せるなら、必ず walk-forward と取引コストモデルを同梱**。過去最適化を勝ち戦略と勘違いさせない UX を。

---

## 出典

### 海外実運用
- [Renaissance Technologies and The Medallion Fund — Quartr](https://quartr.com/insights/edge/renaissance-technologies-and-the-medallion-fund)
- [Decoding the Medallion Fund Returns — QuantifiedStrategies](https://www.quantifiedstrategies.com/medallion-fund-returns/)
- [Jim Simons Trading Strategy Explained — QuantVPS](https://www.quantvps.com/blog/jim-simons-trading-strategy)
- [Renaissance Technologies — Wikipedia](https://en.wikipedia.org/wiki/Renaissance_Technologies)
- [Two Sigma Chaos, Citadel Drawdowns, XTX — Young & Calculated](https://youngandcalculated.substack.com/p/two-sigma-chaos-citadel-drawdowns)
- [AI in Investment Management: 2026 Outlook — Two Sigma](https://www.twosigma.com/articles/ai-in-investment-management-2026-outlook-part-i/)
- [Two Sigma's "AI-First" Internal Mandate — HedgeCo Insights](https://hedgeco.net/news/04/2026/two-sigmas-ai-first-internal-mandate-the-race-for-operational-alpha-in-the-age-of-frontier-models.html)
- [Exploring the Trading Strategies of Two Sigma — BlueChipAlgos](https://bluechipalgos.com/blog/exploring-the-trading-strategies-of-two-sigma/)
- [XTX Markets — Wikipedia](https://en.wikipedia.org/wiki/XTX_Markets)
- [XTX Markets Earnings Jump 33% — Bloomberg (2026)](https://www.bloomberg.com/news/articles/2026-04-02/gerko-s-xtx-markets-earnings-rise-33-on-global-trading-surge)
- [Gerko's XTX Markets Mints $3.53B — Bloomberg (2025)](https://www.bloomberg.com/news/articles/2025-04-04/gerko-s-xtx-markets-mints-3-53-billion-on-global-trading-surge)
- [Secret AI supercomputers powering XTX and DeepSeek — Lex](https://lex.substack.com/p/ai-the-secret-ai-supercomputers-powering)
- [Top AI Trading Bots Earn Up to 171% — Tickeron](https://tickeron.com/trading-investing-101/ai-trading-robots-top-performers-of-the-past-30-days/)
- [AI Trading in 2025: Bots and Machine Learning — Tickeron](https://tickeron.com/blogs/ai-trading-in-2025-how-bots-and-machine-learning-transform-stock-markets-11468/)
- [Top AI Trading Strategies Beating the Market in 2025 — Pure Financial Academy](https://www.purefinancialacademy.com/blog/top-ai-trading-strategies-that-are-beating-the-market-in-2025)
- [Is AI Bot Trading Profitable in 2025? — AgentiveAIQ](https://agentiveaiq.com/blog/is-ai-bot-trading-profitable-the-2025-reality-check)

### 海外 個人・コミュニティ
- [From Failed Experiments to 43.8% APR — Joe Tay (Medium)](https://medium.com/@joetay_50959/from-failed-experiments-to-43-8-apr-how-i-finally-built-a-profitable-trading-bot-with-ai-64771995d38c)
- [720%+ Returns LSTM Crypto — ImbueDesk (Medium)](https://imbuedeskpicasso.medium.com/720-returns-in-3-years-on-cryptocurrency-using-lstm-neural-network-model-and-short-listing-best-6229f941b823)
- [BTC 4750% / ETH 11270% (TCN+LSTM+Transformer) — ImbueDesk](https://imbuedeskpicasso.medium.com/bitcoin-btc-4750-etherium-eth-11-270-profit-in-1023-days-using-neural-networks-algorithmic-d5a644cdc36f)
- [66,941% Returns Ensemble — ImbueDesk](https://imbuedeskpicasso.medium.com/66941-5-returns-in-testing-and-900-live-trades-in-action-a-journey-through-time-series-ensemble-7ad4b833ae9f)
- [2509% Profit Freqtrade Case Study — ImbueDesk](https://imbuedeskpicasso.medium.com/2509-profit-unlocked-a-case-study-on-algorithmic-trading-with-freqtrade-39b1051c0f1e)
- [Freqtrade Backtesting Docs](https://www.freqtrade.io/en/stable/backtesting/)
- [Comprehensive 2025 Guide to Backtesting AI Trading — 3Commas](https://3commas.io/blog/comprehensive-2025-guide-to-backtesting-ai-trading)
- [Real AI Stock Trading Success Stories — Sehat Diri](https://www.sehatdiri.com/2025/10/ai-stock-trading-success.html)
- [AI-Powered Trading: My Experience 2025 — George the Investor (Medium)](https://medium.com/@georgemortoninvest/ai-powered-trading-my-experience-and-insights-for-2025-451d11d2dacc)
- [Backtesting AI Crypto Strategies Safely — Blockchain Council](https://www.blockchain-council.org/cryptocurrency/backtesting-ai-crypto-trading-strategies-avoiding-overfitting-lookahead-bias-data-leakage/)

### 学術論文
- [Improving Deep RL Agent Trading in Forex using Auxiliary Task — arXiv 2411.01456](https://arxiv.org/abs/2411.01456)
- [Reinforcement Learning Applied to Forex Trading — ScienceDirect](https://www.sciencedirect.com/science/article/abs/pii/S1568494618305349)
- [A Deep RL Approach for Forex with Multi-Agent Asynchronous Distribution — arXiv 2405.19982](https://arxiv.org/abs/2405.19982)
- [Reinforcement Learning for Systematic FX Trading — UCL Borrageiro](https://discovery.ucl.ac.uk/id/eprint/10141040/7/Borrageiro_Reinforcement_Learning_for_Systematic_FX_Trading_VoR.pdf)
- [Reinforcement Learning for FX Trading — Stanford MS&E 448](https://stanford.edu/class/msande448/2019/Final_reports/gr2.pdf)
- [Reinforcement Learning for Quantitative Trading — ACM TIST](https://dl.acm.org/doi/fullHtml/10.1145/3582560)
- [ChatGPT in Systematic Investing — arXiv 2510.26228](https://arxiv.org/html/2510.26228v1)
- [Can LLM-based Financial Strategies Outperform the Market Long-run? — arXiv 2505.07078](https://arxiv.org/html/2505.07078v3)
- [Can AI Read the News Better Than You? — Alpha Architect](https://alphaarchitect.com/chatgpt-momentum-investing/)
- [Competing LLMs Were Asked to Pick Stocks — HBR](https://hbr.org/2026/03/competing-llms-were-asked-to-pick-stocks-their-choices-revealed-ais-limitations)
- [Could ChatGPT have earned abnormal returns? — Modern Finance](https://mf-journal.com/article/view/327)

### 国内
- [行動ファイナンスと AI による資産運用 — Magne-Max（researchmap）](https://researchmap.jp/www.magne-max.com/presentations/5084140/attachment_file.pdf)
- [個人もAIで短期売買可能に 新興がヘッジファンド並みシステム供給 — 日経](https://www.nikkei.com/article/DGXZQOUB2431X0U5A420C2000000/)
- [会社概要 — シンプレクス・アセット・マネジメント](https://www.simplexasset.com/etf/company.html)
- [デイトレーダーは生成AIをどう使っているのか？ — MatrixFlow](https://www.matrixflow.net/case-study/181/)
- [AIによるデイトレードで勝てない理由5選 — kmusubi](https://kmusubi.com/blog/day-trading-with-ai/)
- [生成AIを使ったら儲かった4つの攻め技 — 株探ニュース](https://kabutan.jp/news/marketnews/?b=n202512120366)
- [「Analystant」生成AIとバックテストが示す有効性 — AiQ Index](https://www.aiq-index.com/research/analystant-demonstrating-predictive-effectiveness-through-generative-ai-and-backtesting-in-investment-analysis/)
- [AIに株の自動売買戦略を片っ端から検証させた — Zenn (seyz)](https://zenn.dev/seyz/articles/20260221-1622-ai-stock-trading-research)
- [【資産18億円】AIを駆使する87歳・現役トレーダー — ダイヤモンド](https://diamond.jp/articles/-/343274)
- [SBIラップ AI投資コース — SBI証券](https://go.sbisec.co.jp/prd/swrap/aiwrap_top.html)
- [FX自動売買AIエージェントの作り方 — テックジム](https://techgym.jp/column/fxjidobaibai/)
- [PythonとMT5を連携させた機械学習EA — ナンピンマーチン研究](https://nanpin-martin.com/ea-coding_python_103/)
- [ディープラーニングを取り入れたFX自動売買ツール — note(sayama_ocha)](https://note.com/sayama_ocha/n/nea1daf59bc3b)
- [AIモデルを構築して日経平均株価を予測してみた — Qiita](https://qiita.com/kinopy513/items/7ec37b9bab24d6e20192)
- [データサイエンティストが本気で研究したトレード戦略 日経平均デイトレ編 — Zenn](https://zenn.dev/myonie/books/a55eedbba758cc/viewer/c8f63f)
- [日本株 AIモデルが予測する上昇期待の5業種 — マネクリ](https://media.monex.co.jp/articles/-/28245)
- [機械学習を使った株価予測（論文調査） — nehori.com](https://nehori.com/nikki/2020/02/13/post-15594/)
- [MIT Tech Review JP: 機械学習で稼ぐヘッジファンドが躍進中](https://www.technologyreview.jp/nl/hedge-funds-are-increasingly-turning-to-ai-and-that-might-be-a-problem/)
