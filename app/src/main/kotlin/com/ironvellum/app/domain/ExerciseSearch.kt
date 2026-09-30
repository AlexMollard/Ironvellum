package com.ironvellum.app.domain

import java.util.concurrent.ConcurrentHashMap

/**
 * Forgiving exercise-name search shared by every exercise picker.
 *
 * [rank] returns null for no match, otherwise a score where lower is better:
 * exact name < alias < name prefix < every token starts a word < typo.
 * A query that IS a nickname ("fl", "hspu") names one exercise family on
 * purpose, so those hits lead; letter-matches like "Cable Fly" follow.
 */
object ExerciseSearch {
    const val EXACT = 0
    const val ALIAS = 5
    const val PREFIX = 10
    const val WORDS = 20
    const val FUZZY = 40

    /**
     * Nickname -> catalogue phrases. A name matches when it contains a phrase
     * as whole words, so "fl" also finds every Front Lever progression.
     */
    private val aliases: Map<String, List<String>> = buildMap {
        fun a(keys: String, vararg phrases: String) {
            for (k in keys.split(',')) put(compact(k), phrases.toList())
        }
        a("hspu,hs pushup,handstand pushup", "handstand push up")
        a("ohp,press,military press", "overhead press")
        a("rdl", "romanian deadlift")
        a("bp,bench", "bench press")
        a("dl", "deadlift")
        a("sq", "squat")
        a("fl,front lever", "front lever")
        a("bl,back lever", "back lever")
        a("pl", "planche")
        a("mu,mus", "muscle up")
        a("rmu", "ring muscle up")
        a("oap,oa pullup", "one arm pull up")
        a("oapu,oa pushup", "one arm push up")
        a("ttb,t2b,toes 2 bar", "toes to bar")
        a("lsit,l sit", "l sit")
        a("vsit", "v sit")
        a("pistol,pistols", "pistol squat")
        a("nordic,nordics", "nordic curl", "nordic negative")
        a("pu,pushups", "push up")
        a("pullups,chinup,chins", "pull up", "chin up")
        a("kbs", "swing")
        a("hlr,hlrs", "hanging leg raise")
        a("hkr", "hanging knee raise")
        a("hip thrust,glute bridge", "hip thrust", "glute bridge")
        a("hf,human flag", "human flag")
        a("ohs", "overhead squat")
        a("cgbp", "close grip bench press")
    }

    private class Norm(val words: List<String>, val compact: String, val starts: IntArray)

    private val cache = ConcurrentHashMap<String, Norm>()

    private fun words(s: String): List<String> {
        val out = ArrayList<String>(4)
        var i = 0
        while (i < s.length) {
            while (i < s.length && !s[i].isLetterOrDigit()) i++
            val st = i
            while (i < s.length && s[i].isLetterOrDigit()) i++
            if (i > st) out.add(s.substring(st, i).lowercase())
        }
        return out
    }

    private fun compact(s: String): String = words(s).joinToString("")

    private fun norm(name: String): Norm = cache[name] ?: run {
        if (cache.size > 4000) cache.clear()
        val w = words(name)
        val starts = IntArray(w.size)
        var off = 0
        for (i in w.indices) { starts[i] = off; off += w[i].length }
        Norm(w, w.joinToString(""), starts).also { cache[name] = it }
    }

    /** null = no match; lower = better. Blank query matches everything with 0. */
    fun rank(name: String, query: String): Int? {
        val tokens = words(query)
        if (tokens.isEmpty()) return 0
        val n = norm(name)
        val cq = tokens.joinToString("")
        if (n.compact == cq) return EXACT
        aliases[cq]?.let { phrases ->
            if (phrases.any { startsWord(n, it.replace(" ", "")) }) return ALIAS
        }
        if (n.compact.startsWith(cq)) return PREFIX
        if (tokens.all { t -> n.words.any { it.startsWith(t) } } || startsWord(n, cq)) return WORDS
        return if (fuzzyAll(n, tokens)) FUZZY else null
    }

    /** True when [needle] (compact) begins at a word boundary of the name. */
    private fun startsWord(n: Norm, needle: String): Boolean {
        for (s in n.starts) if (n.compact.startsWith(needle, s)) return true
        return false
    }

    private fun maxEdits(len: Int) = when {
        len >= 8 -> 2
        len >= 4 -> 1
        else -> 0
    }

    private fun fuzzyAll(n: Norm, tokens: List<String>): Boolean {
        var anyFuzzy = false
        for (t in tokens) {
            if (n.words.any { it.startsWith(t) }) continue
            val k = maxEdits(t.length)
            if (k == 0) return false
            val hit = n.words.any { w ->
                distance(t, w, k) <= k ||
                    (w.length > t.length && distance(t, w.substring(0, t.length), k) <= k)
            }
            if (!hit) return false
            anyFuzzy = true
        }
        return anyFuzzy
    }

    /** Optimal-string-alignment (Damerau) distance; returns limit + 1 when above [limit]. */
    private fun distance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, prev2[j - 2] + 1)
                }
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
