package com.ironvellum.app.ui.program

import androidx.compose.ui.geometry.Offset
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.Muscle.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The muscle regions are anatomy, not decoration: each is checked against
 * landmarks measured from [HALF_OUTLINE] and from the classical 8-head canon
 * the outline follows, so a region cannot drift into the hand, sit above the
 * nipple line or swallow the whole torso without a test saying so.
 *
 * Landmarks (figure space, y = 0 crown, 1 sole):
 *  - head height = crotch / 4 (the canon puts the crotch at 4 heads)
 *  - nipple line = 2 heads, navel = 3 heads, pubis just above the crotch
 *  - shoulder line = the outline's highest point past the neck; the clavicle
 *    lies just under it
 *  - armpit = the doubled outline vertex; elbow, wrist, knee and ankle = the
 *    pinched (narrowest) outer vertex of their limb
 */
class FigureAnatomyTest {

    private val k = 1000f

    private fun curve(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> =
        smoothSamples(points.map { (x, y) -> Offset(x * k, y * k) }).map { it.x / k to it.y / k }

    private val body = curve(HALF_OUTLINE + HALF_OUTLINE.reversed().map { (x, y) -> -x to y })

    private fun inside(poly: List<Pair<Float, Float>>, p: Pair<Float, Float>): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val (xi, yi) = poly[i]
            val (xj, yj) = poly[j]
            if ((yi > p.second) != (yj > p.second) && p.first < (xj - xi) * (p.second - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    private fun crossings(poly: List<Pair<Float, Float>>, y: Float): List<Float> =
        poly.indices.mapNotNull { i ->
            val (x0, y0) = poly[i]
            val (x1, y1) = poly[(i + 1) % poly.size]
            if ((y0 > y) != (y1 > y)) x0 + (y - y0) * (x1 - x0) / (y1 - y0) else null
        }

    private fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
        (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)

    private fun overlaps(p: List<Pair<Float, Float>>, q: List<Pair<Float, Float>>): Boolean {
        for (i in p.indices) for (j in q.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]; val c = q[j]; val d = q[(j + 1) % q.size]
            if (cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0) return true
        }
        return inside(p, q[0]) || inside(q, p[0])
    }

    private class Shape(val muscle: Muscle, val s: List<Pair<Float, Float>>)

    private fun shapes(view: List<Region>, vararg muscles: Muscle) =
        view.filter { it.muscle in muscles }.map { Shape(it.muscle, curve(it.points)) }

    private val List<Shape>.minX get() = minOf { sh -> sh.s.minOf { it.first } }
    private val List<Shape>.maxX get() = maxOf { sh -> sh.s.maxOf { it.first } }
    private val List<Shape>.top get() = minOf { sh -> sh.s.minOf { it.second } }
    private val List<Shape>.bottom get() = maxOf { sh -> sh.s.maxOf { it.second } }
    private val List<Shape>.height get() = bottom - top

    /** Horizontal extent of the samples between [y0] and [y1], across every shape. */
    private fun List<Shape>.span(y0: Float, y1: Float): Float {
        val xs = flatMap { it.s }.filter { it.second in y0..y1 }.map { it.first }
        return if (xs.isEmpty()) 0f else xs.max() - xs.min()
    }

    private val front = FRONT
    private val back = BACK
    private val views = mapOf("front" to FRONT, "back" to BACK)

    // ---- landmarks ----
    private val crotchY = HALF_OUTLINE.last().second
    private val head = crotchY / 4f
    private val nippleY = 2f * head
    private val navelY = 3f * head
    private val pubisY = crotchY - 0.02f
    private val iliacY = 3.5f * head
    private val midBackY = 2.5f * head
    private val acromionY = HALF_OUTLINE.filter { it.first > 0.1f }.minOf { it.second }
    private val clavicleY = acromionY + 0.006f
    private val armpitY = HALF_OUTLINE.withIndex().first { (i, p) -> i > 0 && p == HALF_OUTLINE[i - 1] }.value.second
    private val armpitX = HALF_OUTLINE.withIndex().first { (i, p) -> i > 0 && p == HALF_OUTLINE[i - 1] }.value.first

    private fun pinchY(ys: ClosedFloatingPointRange<Float>, xMin: Float) =
        HALF_OUTLINE.filter { it.second in ys && it.first > xMin }.minBy { it.first }.second

    private val elbowY = pinchY(0.28f..0.36f, 0.14f)
    private val wristY = pinchY(0.40f..0.47f, 0.14f)
    private val kneeY = pinchY(0.65f..0.75f, 0.07f)
    private val ankleY = pinchY(0.88f..0.95f, 0.05f)

    /** The torso's edge at the nipple line; nipples sit about half-way out, the abs about as wide as the nipples are apart. */
    private val torsoHalf = crossings(body, nippleY).filter { it > 0f }.min()
    private val absMaxX = torsoHalf * 0.6f

    // ---- the whole figure ----

    @Test
    fun `landmarks come out where the canon says`() {
        assertEquals(0.505f, crotchY, 0.001f)
        assertEquals(0.2525f, nippleY, 0.001f)
        assertEquals(0.333f, elbowY, 0.002f)
        assertEquals(0.452f, wristY, 0.002f)
        assertEquals(0.700f, kneeY, 0.002f)
        assertEquals(0.922f, ankleY, 0.002f)
        assertEquals(0.228f, armpitY, 0.002f)
    }

    @Test
    fun `each view draws the muscles that belong on it`() {
        assertEquals(
            setOf(TRAPS, FRONT_DELTS, SIDE_DELTS, UPPER_CHEST, MID_CHEST, LOWER_CHEST, SERRATUS, BICEPS, BRACHIALIS, FOREARMS, ABS, OBLIQUES, HIP_FLEXORS, QUADS, ADDUCTORS, TIBIALIS, CALVES),
            front.map { it.muscle }.toSet(),
        )
        assertEquals(
            setOf(TRAPS, RHOMBOIDS, ROTATOR_CUFF, REAR_DELTS, LATS, TRICEPS, FOREARMS, LOWER_BACK, ABDUCTORS, GLUTES, HAMSTRINGS, CALVES),
            back.map { it.muscle }.toSet(),
        )
    }

    @Test
    fun `every region lies inside the body outline`() {
        for ((name, view) in views) for (r in view) {
            val outside = curve(r.points).filterNot { inside(body, it) }
            assertTrue("$name ${r.muscle} pokes outside the outline at ${outside.firstOrNull()}", outside.isEmpty())
        }
    }

    @Test
    fun `no two regions on a view overlap`() {
        for ((name, view) in views) {
            val c = view.map { it.muscle to curve(it.points) }
            for (i in c.indices) for (j in i + 1 until c.size) {
                assertTrue("$name: ${c[i].first} #$i overlaps ${c[j].first} #$j", !overlaps(c[i].second, c[j].second))
            }
        }
    }

    // ---- front ----

    private val chest = shapes(front, UPPER_CHEST, MID_CHEST, LOWER_CHEST)
    private val abs = shapes(front, ABS)
    private val serratus = shapes(front, SERRATUS)

    @Test
    fun `the pec is a fan from the clavicle down to the nipple line`() {
        assertEquals("three chest tiers", 3, front.count { it.muscle in setOf(UPPER_CHEST, MID_CHEST, LOWER_CHEST) })
        assertTrue("pec top ${chest.top} should sit just under the clavicle $clavicleY", chest.top in clavicleY..(clavicleY + 0.012f))
        assertTrue("pec bottom ${chest.bottom} must reach the nipple line $nippleY", chest.bottom >= nippleY)
        assertTrue("pec bottom ${chest.bottom} is not a belly-sized plate", chest.bottom <= nippleY + 0.025f)
        assertTrue("pec should be at least 0.6 head tall, is ${chest.height}", chest.height >= 0.6f * head)
        // The fan converges: tall at the sternum, a point at the arm.
        val lateralTip = chest.flatMap { it.s }.filter { it.first > 0.103f }
        val tipHeight = lateralTip.maxOf { it.second } - lateralTip.minOf { it.second }
        assertTrue("pec tip $tipHeight should be under a third of ${chest.height}", tipHeight < chest.height / 3f)
    }

    @Test
    fun `the pec reaches its point near the armpit`() {
        val tip = chest.flatMap { it.s }.maxBy { it.first }
        assertTrue("pec tip x ${tip.first} should be within 0.02 of the armpit x $armpitX", kotlin.math.abs(tip.first - armpitX) <= 0.02f)
        assertTrue("pec tip y ${tip.second} should be just above the armpit crease $armpitY", tip.second in (armpitY - 0.04f)..armpitY)
    }

    @Test
    fun `the deltoid is a shield that tapers to a point on the arm`() {
        val delt = shapes(front, FRONT_DELTS, SIDE_DELTS)
        val h = delt.height
        assertTrue("delt cap starts at the shoulder line", delt.top <= acromionY + 0.015f)
        assertTrue("delt tip ${delt.bottom} should run well below the armpit $armpitY", delt.bottom >= armpitY + 0.02f)
        assertTrue("delt should taper: bottom ${delt.span(delt.bottom - 0.2f * h, delt.bottom)} vs top ${delt.span(delt.top, delt.top + 0.2f * h)}",
            delt.span(delt.bottom - 0.2f * h, delt.bottom) < 0.5f * delt.span(delt.top, delt.top + 0.2f * h))
    }

    @Test
    fun `the serratus is finger-like, several slips`() {
        assertTrue("serratus slips: ${serratus.size}", serratus.size >= 3)
    }

    @Test
    fun `the serratus sits below the armpit beside the abs and the pec's lower edge`() {
        assertTrue("serratus top ${serratus.top} must be below the armpit $armpitY", serratus.top > armpitY)
        assertTrue("serratus starts ${serratus.minX}, lateral to the abs (${abs.maxX})", serratus.minX > abs.maxX)
        assertTrue("serratus top ${serratus.top} should overlap the pec's lower part (pec bottom ${chest.bottom})", serratus.top < chest.bottom - 0.01f)
        assertTrue("serratus must continue below the pec, not sit entirely in it", serratus.bottom > chest.bottom)
        assertTrue("serratus is not entirely below the pec", serratus.top < chest.bottom)
    }

    @Test
    fun `the abs are a narrow segmented column from the pecs to the pubis`() {
        assertTrue("abs are ${abs.maxX} wide, limit $absMaxX", abs.maxX <= absMaxX)
        assertTrue("abs begin ${abs.top} just under the pec ${chest.bottom}", abs.top >= chest.bottom && abs.top <= chest.bottom + 0.03f)
        assertTrue("abs end ${abs.bottom} near the pubis $pubisY", abs.bottom in (pubisY - 0.02f)..(pubisY + 0.01f))
    }

    @Test
    fun `the abs are three rows above the navel and a longer lower section`() {
        assertEquals("three rows above the navel", 3, abs.count { it.s.maxOf { p -> p.second } < navelY })
        assertTrue("then a longer lower section across the navel's level and below",
            abs.count { it.s.minOf { p -> p.second } > navelY - 0.01f && it.s.maxOf { p -> p.second } - it.s.minOf { p -> p.second } > 0.07f } == 1)
    }

    @Test
    fun `the obliques are lateral slabs between the ribs and the hip`() {
        val o = shapes(front, OBLIQUES)
        assertTrue("obliques start ${o.minX} lateral to the abs ${abs.maxX}", o.minX > abs.maxX)
        assertTrue("obliques begin at the lower ribs, ${o.top}", o.top >= 2.3f * head)
        assertTrue("obliques end at the iliac crest, ${o.bottom}", o.bottom <= crotchY - 0.03f)
        assertTrue("obliques reach the iliac crest level $iliacY", o.bottom >= iliacY)
    }

    @Test
    fun `biceps is a spindle between the shoulder and the elbow`() {
        val b = shapes(front, BICEPS)
        assertTrue("biceps top ${b.top}", b.top > armpitY - 0.01f)
        assertTrue("biceps bottom ${b.bottom} above the elbow $elbowY", b.bottom <= elbowY)
        val h = b.height
        val mid = b.span(b.top + 0.3f * h, b.top + 0.7f * h)
        assertTrue("biceps belly $mid should be twice its tips", mid >= 2f * b.span(b.bottom - 0.1f * h, b.bottom) && mid >= 2f * b.span(b.top, b.top + 0.1f * h))
        val bra = shapes(front, BRACHIALIS)
        assertTrue("brachialis stays on the upper arm, ${bra.top}..${bra.bottom}", bra.top > armpitY && bra.bottom <= elbowY)
        assertTrue("brachialis lies outside the biceps", bra.minX >= b.minX && bra.maxX > b.maxX)
    }

    @Test
    fun `the forearm is a cone that ends in tendon above the wrist`() {
        for ((name, view) in views) {
            val f = shapes(view, FOREARMS)
            assertTrue("$name forearm ends ${f.bottom}, wrist is $wristY", f.bottom <= wristY - 0.02f)
            assertTrue("$name forearm starts at the elbow, ${f.top}", f.top >= elbowY - 0.005f && f.top <= elbowY + 0.02f)
            val h = f.height
            val top = f.span(f.top, f.top + 0.15f * h)
            val bottom = f.span(f.bottom - 0.15f * h, f.bottom)
            assertTrue("$name forearm should taper: $bottom at the tendon vs $top at the elbow", bottom < 0.5f * top)
        }
    }

    @Test
    fun `the thigh is adductors then three quad heads between the crotch and the knee`() {
        val q = shapes(front, QUADS)
        assertTrue("vastus medialis, rectus femoris, vastus lateralis", q.size >= 3)
        assertTrue("quads begin at the crotch line, ${q.top}", q.top >= crotchY - 0.03f)
        assertTrue("quads end ${q.bottom} above the knee $kneeY", q.bottom <= kneeY)
        val a = shapes(front, ADDUCTORS)
        assertTrue("adductors begin at the crotch, ${a.top}", a.top >= crotchY - 0.03f)
        assertTrue("adductors taper out above the knee, ${a.bottom}", a.bottom < kneeY - 0.05f)
        assertTrue("adductors are the inner thigh", a.minX < q.minX && a.maxX < q.maxX)
    }

    @Test
    fun `the lower leg is the shin between two calf heads, knee to ankle`() {
        for (m in listOf(shapes(front, CALVES), shapes(front, TIBIALIS), shapes(back, CALVES))) {
            assertTrue("${m.first().muscle} starts ${m.top} below the knee $kneeY", m.top >= kneeY)
            assertTrue("${m.first().muscle} ends ${m.bottom} above the ankle $ankleY", m.bottom <= ankleY)
        }
        assertEquals("two calf heads seen from the front", 2, front.count { it.muscle == CALVES })
        assertTrue("two heads and the soleus from behind", back.count { it.muscle == CALVES } >= 3)
    }

    // ---- back ----

    @Test
    fun `triceps is a horseshoe between the shoulder and the elbow`() {
        val t = shapes(back, TRICEPS)
        assertTrue("triceps top ${t.top}", t.top > armpitY - 0.01f)
        assertTrue("triceps bottom ${t.bottom} above the elbow $elbowY", t.bottom <= elbowY)
        val lobes = crossings(t.first().s, t.bottom - 0.012f).size
        assertEquals("a section near the elbow cuts both lobes of the horseshoe", 4, lobes)
    }

    @Test
    fun `traps are a diamond from the skull base to mid-back spreading to the shoulders`() {
        val t = shapes(back, TRAPS)
        assertTrue("traps start at the skull base, ${t.top}", t.top <= 0.13f)
        assertTrue("traps point down to mid-back, ${t.bottom}", t.bottom in (midBackY - 0.01f)..(midBackY + 0.03f))
        assertTrue("traps spread to the shoulder, ${t.maxX}", t.maxX >= 0.115f)
        assertTrue("traps are widest at the shoulders: ${t.span(0.15f, 0.17f)} vs ${t.span(0.28f, 0.30f)}", t.span(0.15f, 0.17f) > 4f * t.span(0.28f, 0.30f))
    }

    @Test
    fun `lats are a wing from the armpit to the lower back`() {
        val l = shapes(back, LATS)
        assertTrue("lats begin at the armpit, ${l.top}", l.top in armpitY..(armpitY + 0.02f))
        assertTrue("lats reach the lower back, ${l.bottom}", l.bottom >= iliacY - 0.03f)
        val h = l.height
        assertTrue("lats taper downward", l.span(l.top, l.top + 0.2f * h) > 2f * l.span(l.bottom - 0.2f * h, l.bottom))
    }

    @Test
    fun `scapula muscles sit between the spine and the shoulder`() {
        val r = shapes(back, RHOMBOIDS)
        assertTrue("rhomboids between spine and scapula, x ${r.minX}..${r.maxX}", r.minX >= 0.01f && r.maxX <= 0.06f)
        assertTrue("rhomboids at the upper-mid back, y ${r.top}..${r.bottom}", r.top >= 0.19f && r.bottom <= 0.28f)
        val c = shapes(back, ROTATOR_CUFF)
        assertTrue("rotator cuff on the scapula, y ${c.top}..${c.bottom}", c.top >= 0.18f && c.bottom <= armpitY + 0.03f)
        assertTrue("rotator cuff lateral to the rhomboids, x ${c.minX}", c.minX >= r.maxX)
        val d = shapes(back, REAR_DELTS)
        assertTrue("rear delt caps the shoulder, ${d.top}", d.top <= acromionY + 0.015f)
        assertTrue("rear delt tapers onto the arm, ${d.bottom}", d.bottom >= armpitY + 0.02f)
    }

    @Test
    fun `erectors run beside the spine`() {
        val e = shapes(back, LOWER_BACK)
        assertTrue("erectors hug the spine, x ${e.maxX}", e.maxX <= 0.045f)
        assertTrue("erectors run from mid-back to the sacrum, ${e.top}..${e.bottom}", e.top >= midBackY && e.bottom >= iliacY)
    }

    @Test
    fun `glutes are a rounded square below the iliac crest, hamstrings two bellies down to the knee`() {
        val g = shapes(back, GLUTES)
        assertTrue("glutes begin below the iliac crest, ${g.top}", g.top >= iliacY)
        val aspect = (g.maxX - g.minX) / g.height
        assertTrue("glutes are roughly square, aspect $aspect", aspect in 0.6f..1.4f)
        val m = shapes(back, ABDUCTORS)
        assertTrue("gluteus medius sits above the glute max, ${m.bottom}", m.bottom <= g.top + 0.03f && m.top >= iliacY - 0.03f)
        val h = shapes(back, HAMSTRINGS)
        assertEquals("two hamstring bellies", 2, h.size)
        assertTrue("hamstrings start under the glutes, ${h.top}", h.top >= g.bottom - 0.01f - 0.005f)
        assertTrue("hamstrings end ${h.bottom} above the knee $kneeY", h.bottom <= kneeY)
    }
}
