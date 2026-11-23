# Desktop typefaces

The executable can embed these fonts without retrieving files or consulting installed fonts. Chakra Petch Medium, Semibold and Bold are byte-for-byte copies of the Android assets in `app/src/main/res/font`. See `OFL-ChakraPetch.txt` for the original copyright and SIL Open Font License. The source project for the regional Noto fonts is [Noto CJK](https://github.com/notofonts/noto-cjk). Their full Adobe copyright and SIL Open Font License accompany them in `OFL-NotoSans.txt`.

## Source and transformation

The three Noto Sans 2.004 variable TTFs come from [Google Fonts revision 66a36c8c94b1a5d992ee4e7f392fccfe4945767c](https://github.com/google/fonts/tree/66a36c8c94b1a5d992ee4e7f392fccfe4945767c/ofl). Each source path is `ofl/notosans{jp,kr,sc}/NotoSans{JP,KR,SC}[wght].ttf`.

| Face | Source bytes | Source SHA-256 |
| --- | ---: | --- |
| JP | 9589900 | c2f3b4d463500a2ddcd3849cded1fceeb9fd6d1c32e6cbecd568453ba50fc68f |
| KR | 10414588 | 194018e6b2b293a7964f037b25c0249ce1418bc9ab3c971060a03aa57861e252 |
| SC | 17772300 | a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da |

The original variable fonts default to weight 100. The distributed derivatives freeze weight 400 using [HarfBuzz 12.3.0](https://github.com/harfbuzz/harfbuzz/releases/tag/12.3.0). The upstream Windows x64 archive SHA-256 is `edc6af11d11ef58e32d584d6fd42bd2ed89672c4b434417eae25f57409491f1e`. To reproduce for each original, run the commands below from the desktop crate, substituting the three filenames. The second step restores the original complete cmap, including its legacy Macintosh and variation-selector data, corrects the stale Thin labels and recalculates the font checksums. It preserves copyright and license name records.

```text
hb-subset ORIGINAL.ttf --variations=wght=400 --unicodes=* --glyphs=* --retain-gids --layout-features=* --layout-scripts=* --name-IDs=* --name-languages=* --name-legacy --glyph-names --notdef-outline --passthrough-tables -o INSTANCE.ttf
node tools/label-static-font.cjs ORIGINAL.ttf INSTANCE.ttf FINAL.ttf
```

The source-derived reference files in `tests/reference` contain two 80×80 grayscale glyphs, 骨 and 直, drawn with the resolved Rust rasterizer at weight 400 for each locale. Rebuild one with `cargo run --locked --example qualify_font -- ORIGINAL.ttf tests/reference/REGION.gray`. The cmap test compares the complete table, including format 14, against digests measured on the corresponding pinned original.

## Distributed files

| Filename | Bytes | SHA-256 |
| --- | ---: | --- |
| chakra_petch_medium.ttf | 78600 | d480f4f97405fac3600652e2e14fd0b14339031c0af4da11582994878f04d919 |
| chakra_petch_semibold.ttf | 78604 | 45264de3204ddbd5fb3e14a2402acd5c630d16650ae5fc221d2c52da46a6734b |
| chakra_petch_bold.ttf | 78384 | 65fbf76d95651697275e19db4d717c0e95a789ddd3476478b05292104db278a0 |
| NotoSansJP-Regular.ttf | 5765432 | 803c94889814c8d1fdbc1b1d070b743900c0909dc49412b27e25269bed1564fb |
| NotoSansKR-Regular.ttf | 6223316 | 75fe5297ef10d7dc139755e130f73fbc059f453d5b92c01a14c050aad2822cb9 |
| NotoSansSC-Regular.ttf | 10594780 | c16a06ea5658a78a00020b005dea8084c5e912fa7821bc92aa56dfcdf2b9bef8 |
| OFL-ChakraPetch.txt | 4489 | af39909c36b215eef56267c54843b6ffa9d2a81a8850fdeffde82bd9e4c58238 |
| OFL-NotoSans.txt | 4388 | 1c05c68c34f9708415aada51f17e1b0092d2cea709bf4a94cd38114f9e73d7d9 |
