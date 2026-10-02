package com.ironvellum.app.ui.settings

/**
 * The notices the app must carry for code and data it did not write. The licence text is shipped here, in the app
 * itself, because the muscle map shapes are compiled in (ui/program/BodyMapShapes.kt) and the MIT licence asks that
 * its notice travel with every copy. The same text is in licenses/ and THIRD_PARTY_NOTICES.md.
 */
internal object OpenSourceNotices {
    /** One line for the licence panel. */
    const val BODY_MAP_CREDIT =
        "Muscle map body shapes and outlines derived from react-native-body-highlighter, MIT, " +
            "\u00A9 2022 ELABBASSI Hicham."

    /** Where it comes from, for the full-text view. */
    const val BODY_MAP_SOURCE = "https://github.com/HichamELBSI/react-native-body-highlighter"

    /** The licence, verbatim. */
    val BODY_MAP_MIT: String = """
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
""".trimIndent()
}
