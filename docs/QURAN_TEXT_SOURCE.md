# Bundled Quran text

The app contains only these Arabic readings, in Tanzil's Uthmani script with Hafs verse numbering:

| Asset | Passage | Numbered verses |
|---|---|---|
| `app/src/main/assets/quran/yaseen.json` | Surah Ya-Sin, Quran 36:1–83 | 83, numbered 1 through 83 |
| `app/src/main/assets/quran/ayat_kareema.json` | Quran 21:87 | 1, numbered 87; the complete verse |
| `app/src/main/assets/quran/ayatul_kursi.json` | Quran 2:255 | 1, numbered 255; the complete verse |

Ayat-e-Karima contains the complete Quran 21:87, including the narrative opening. It is explicitly identified as a complete verse in the app. No abbreviated invocation, translation, transliteration, audio, image or font has been generated or bundled.

## Source and immutable provenance

The original text is the [Tanzil Project Quran Text](https://tanzil.net), downloaded upstream as the `uthmani` variant from:

`https://tanzil.net/pub/download/index.php?quranType=uthmani&outType=txt-2&agree=true`

Direct Tanzil and Al Quran Cloud requests were unavailable through the cloud environment's network policy. The text was therefore obtained from the publicly attributed, committed Tanzil snapshot in [Quran JSON by Risan Bagja Pradana](https://github.com/risan/quran-json/tree/6523a9ad3b73bb7e8cb3a9cdf0ea774b8bab3123), not transcribed from memory.

- Mirror commit: `6523a9ad3b73bb7e8cb3a9cdf0ea774b8bab3123`.
- Snapshot: [`data/tanzil/uthmani.json`](https://github.com/risan/quran-json/blob/6523a9ad3b73bb7e8cb3a9cdf0ea774b8bab3123/data/tanzil/uthmani.json).
- Snapshot SHA-256: `adaebb377c60eba1bab6ef652959c7a0b4ccb4d0ca42145945c14ecdbeb4b761`.
- The downloaded snapshot's SHA-256 was checked against the mirror's committed [`data/meta/sources.json`](https://github.com/risan/quran-json/blob/6523a9ad3b73bb7e8cb3a9cdf0ea774b8bab3123/data/meta/sources.json).
- Tanzil's complete copyright notice was obtained from the mirror's [`TANZIL_NOTICE`](https://github.com/risan/quran-json/blob/6523a9ad3b73bb7e8cb3a9cdf0ea774b8bab3123/src/quranjson/config.py) and is retained in **each JSON asset** and `quran/NOTICE.txt`.

All 85 numbered verse texts were independently compared with [semarketir/quranjson](https://github.com/semarketir/quranjson/tree/7ca6f46ec9a1a029cdb220c2531d509f4a5b5e98) at commit `7ca6f46ec9a1a029cdb220c2531d509f4a5b5e98`, using its `source/surah/surah_36.json`, `surah_21.json` and `surah_2.json`. All verse IDs and Arabic base-letter sequences matched. That secondary copy uses some different combining marks and pause symbols; comparison discarded combining/pause marks and normalized alif-wasla to alif **only for the comparison**. The bundled text retains the primary Tanzil snapshot's exact letters, diacritics and punctuation. This independent comparison is a consistency check, not a claim that the secondary copy is byte-identical or a substitute for scholarly review.

## Basmala handling

Tanzil includes the chapter-opening basmala followed by a space at the start of its row for 36:1. The app presents that same basmala once as an unnumbered chapter opening and presents Ya-Sin verse 1 separately. No Arabic character was rewritten: rejoining the stored basmala, the original space and the first verse exactly reproduces the upstream 36:1 text. The basmala is also identical to the upstream 1:1 string.

`QuranContent.load(context, "yaseen")` returns exactly 83 numbered verses. `QuranContent.basmala(context, "yaseen")` returns the separate verified opening. The two individual-verse readings return no extra basmala. The reader must not prepend another basmala to the numbered verse text.

## Redistribution and attribution

[Tanzil's terms](https://tanzil.net/docs/text_license) identify the text as **Copyright (C) 2007-2021 Tanzil Project**, under [Creative Commons Attribution 3.0](https://creativecommons.org/licenses/by/3.0/), with permission to redistribute verbatim copies and an explicit prohibition on changing the text. The copyright notice, source credit and a link to Tanzil must remain with redistributed copies. The reader therefore needs a visible Tanzil credit and a working link to `https://tanzil.net`; `QuranContent.ATTRIBUTION` and `SOURCE_URL` provide these values.

The intermediary Quran JSON dataset is [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). Its attribution is included in the offline notice; its full license is bundled as `quran/MIRROR_LICENSE.txt`. That dataset license does not replace the original Tanzil text terms.

Future updates should start from a new verified upstream snapshot, preserve the original text and notices, and rerun the full verse range and text-content checks. Do not hand-edit Arabic text to address a display or font problem.

## Verification in the repository

`QuranContentTest` checks exact expected verse ranges, known opening/closing verse boundaries, complete verse text hashes, separate basmala handling, license notices and rejection of unsupported reading IDs. The runtime parser also rejects incomplete verse ranges. All content loads solely from packaged UTF-8 assets and requires no network connection.

The Quran reading screens do not request or display advertisements. Counter-screen advertising is managed separately by the app.
