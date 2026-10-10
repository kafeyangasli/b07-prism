# PDF font

Noto Sans Regular is bundled so PDF exports do not depend on host fonts.

- Source: https://github.com/notofonts/noto-fonts/blob/main/hinted/ttf/NotoSans/NotoSans-Regular.ttf
- License: SIL Open Font License 1.1, included in `OFL.txt`.
- SHA-256: `b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5`.
- Coverage includes Indonesian/Latin text, accented Latin, common punctuation, Greek and Cyrillic. This font does not cover every Unicode script or emoji, and the exporter does not implement complex-script shaping.

Characters outside its coverage are visibly represented by their Unicode identity (for example `[U+1F984]`). They are never silently replaced with `?`. Other export formats retain the original characters. Add a properly licensed font and shaping support if exact rendering of additional scripts is required.
