# Third-party notices

Ironvellum is GPL-3.0-or-later (see LICENSE). It includes the following material from others.

## Muscle map body shapes and outlines

- Source: [react-native-body-highlighter](https://github.com/HichamELBSI/react-native-body-highlighter),
  `main` as fetched 2026-10-02.
- Taken from it: the muscle and head shapes of the male and female bodies (`assets/bodyFront.ts`,
  `bodyBack.ts`, `bodyFemaleFront.ts`, `bodyFemaleBack.ts`) and the hand-drawn border paths and viewBoxes of
  `components/SvgMaleWrapper.tsx` and `SvgFemaleWrapper.tsx`, used as the bodies' outlines.
- Converted by `tools/bodymap_import.py` into `app/src/main/kotlin/com/ironvellum/app/ui/program/BodyMapShapes.kt`.
  The source files are not in this repository. The hair, the female head and the neck lines are drawn by the
  tool, not taken from the source.
- Licence: MIT, Copyright (c) 2022 ELABBASSI Hicham. The text is below, in `licenses/`, and in the app under
  Support, Licence.

```
MIT License

Copyright (c) 2022 ELABBASSI Hicham

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Chakra Petch font

Copyright 2018 The Chakra Petch Project Authors, SIL Open Font License 1.1. See `licenses/OFL-ChakraPetch.txt`.
