# Glossary term table — Japanese (`ja`)

One translation per concept, used in every string. Plain column for `key`, themed for `key__t`.
Register: neutral polite (です・ます body, noun-style buttons as common in fitness apps). Count characters, not bytes.

## Glossary concepts (plain / themed)

| Concept | Plain (ja) | Themed (ja) | Notes |
|---|---|---|---|
| workout / trial | ワークアウト | 試練 | |
| routine / rite | ルーチン | 儀礼 | |
| weekly plan / cycle | ウィークリープラン | 周期 | |
| streak / oath | ストリーク | 誓い | |
| PR / peak | 自己ベスト | 頂 | 「自己ベスト更新!」/「頂!」 |
| history / Chronicle | 履歴 | 年代記 | |
| friends / allies | フレンド | 味方 | |
| group / Circle | グループ | 結社 | |
| owner (of group) / Keeper | 管理者 | 守護手 | badge |
| feed / Tidings | フィード | 報せ | |
| leaderboard / The Reckoning | ランキング | 審判 | |
| tier / Rung | 階級 | 位階 | |
| position / Standing | 順位 | 位 | 「あなたの順位」/「あなたの位」 |
| kudos / Tribute | 称賛 | 貢ぎ物 → 貢 | reactions: 敬意、鉄、炎 |
| comment / Remark | コメント | 言 | |
| inbox / Missives | 受信箱 | 書簡 | |
| body / Frame | 体 | 体躯 | |
| profile / Folio | プロフィール | 巻物 | |
| display name / True name | 表示名 | 真の名 | |
| lifter / Ironbound | トレーニー | 鉄縛り | app addresses あなた |
| finish (a workout) / Seal | 終了 | 封じる | 「試練を封じる」 |
| rest day / Respite | 休息日 | 休 | |
| reminder / The Summons | リマインダー | 呼び声 | |
| setup / The Binding | 初期設定 | 絆 | |
| equipment / Armoury | 器具 | 武具庫 | |
| plan builder / The Forge | プラン作成 | 鍛冶場 | verb 鍛える |
| improve / Temper | 改善 | 研ぐ | |
| templates / Patterns | テンプレート | 型 | |
| progress (tab) / Codex | 進捗 | 大書 | |
| skills / Paths | スキル | 道 | 「引くの道」 |
| skill / Technique | テクニック | 技 | 「技を5つ極める」 |
| practice log / Journal | 練習記録 | 日誌 | |
| stats (tab) / Ledger | 統計 | 台帳 | narrator: 台帳 |
| measurement / Reading | 測定 | 記 | 「測定を記録」 |
| empty workout / Open Trial | フリーワークアウト | 自由なる試練 | |
| today's workout | 今日のワークアウト | 本日の試練 | |
| custom (exercise) / Yours | 自作 | 汝の | |
| ascension tier | same both modes | 見習い · 熟練者 · 守護者 · 魔導師 · 執政官 · 総督 · 君主 | |
| strength rank | same | 未評価, 未トレーニング, 初級, 中級, 上級, エリート | |

## `t` (as-a-proper-noun) form rule

The `t` form of a concept is the themed name written mid-sentence as a proper noun (docs/STRINGS.md).
For a plain-mode `key` the `t` form equals `s`. For a themed-mode `key__t` the `t` form is capitalised-as-a-name
style: for CJK, where case does not exist, use the same word — do not switch to a different term.

## Reward layer (both modes, stays themed)

| Concept | ja |
|---|---|
| Deed | 功績 |
| Title (of deed) | 称号 |
| Wear | 着用 |
| Worn | 着用中 |
| Rarity tiers | 一般 · 稀少 · 伝説 · 秘宝 |
| The Veil | 帳 |
| Echo | 残響 |
| Essence | 精髄 |
| Inscribe | 刻む |
| Inscription | 刻印 |
| Relic | 遺物 |
| Vault | 宝庫 |
| Crest | 紋章 |
| Rank ladder | 見習い · 熟練者 · 守護者 · 魔導師 · 執政官 · 総督 · 君主 |

## Gym terms (always plain, lifter's common form)

| Term | ja |
|---|---|
| RPE | RPE (keep;「RPE: 主観的運動強度、10が限界」) |
| RIR | RIR (keep;「残りレップ数」) |
| PR | 自己ベスト |
| set | セット |
| rep | レップ |
| rest | 休息 |
| warm-up | ウォームアップ |
| top set | トップセット |
| back-off | バックオフセット |
| deload | デロード |
| kg / lb | kg / lb |
| 1-rep max | 1RM |
| drop set | ドロップセット |
| failure | 限界 |
| rest-pause | リストポーズ |
| superset | スーパーセット |
| tempo | テンポ |
| load | 重量 |
| volume | 総負荷量 |
| hypertrophy | 筋肥大 |
| BMI / FFMI / body fat | BMI / FFMI / 体脂肪率 |
| steps / sleep / calories | 歩数 / 睡眠 / カロリー |
| XP / LV | XP / Lv |

## Voice

- Buttons short noun style: 「開始」「続行」「保存」「破棄」.
- Notifications polite: 「今日のワークアウトが待っています」.
- `% (optional)` and `40% more` style strings: keep a space after the `%` exactly as in
  English — `%o` would be read as a format placeholder by aapt2.
- Themed narrator (台帳): ink/vellum register (「台帳がつまずいた — しばらくして再試行」).
- No plural/article distinction: pl forms equal the singular; a/ca forms drop the article.
