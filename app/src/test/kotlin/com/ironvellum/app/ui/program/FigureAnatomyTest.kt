package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.Muscle.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

private typealias P = Pair<Float, Float>

/**
 * The muscle regions are anatomy, not decoration: each is checked against landmarks measured from the
 * body's own outline and from the classical 8-head canon, so a region cannot drift into the hand, sit above
 * the nipple line or swallow the whole torso without a test saying so. One subclass per body runs the same
 * contract against its own figure.
 *
 * Landmarks (figure space, y = 0 crown, 1 sole):
 *  - head height = crotch / 4 (the canon puts the crotch at 4 heads); nipple line 2 heads, navel 3
 *  - shoulder line = the outline's highest point past the neck; the clavicle lies just under it
 *  - armpit = the first row below the shoulder where the outline has three edges right of the midline
 *    (torso side, inside of the arm, outside of the arm); the torso's half-width there is the abs' ruler
 *  - elbow and wrist sit 1.2 and 2.3 heads below the shoulder line; knee and ankle are the narrowest rows
 *    of the leg's outline, between the thigh and the calf and above the foot
 *
 * Thresholds that differ from the hand-drawn figure's are the source shapes' own measurements (the
 * source's forearm ends at the wrist crease, its pec is a rounded plate cut along a fan, its trapezius is
 * the central diamond), each with the reason beside it. None was loosened to let a misplaced region pass.
 */
abstract class FigureAnatomyTest {

    internal abstract val figure: BodyFigure

    private val body: List<P> by lazy { figure.fullOutline }
    private val front: List<Region> get() = figure.front
    private val back: List<Region> get() = figure.back

    private fun inside(poly: List<P>, p: P): Boolean {
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

    private fun crossings(poly: List<P>, y: Float): List<Float> =
        poly.indices.mapNotNull { i ->
            val (x0, y0) = poly[i]
            val (x1, y1) = poly[(i + 1) % poly.size]
            if ((y0 > y) != (y1 > y)) x0 + (y - y0) * (x1 - x0) / (y1 - y0) else null
        }

    private fun cross(o: P, a: P, b: P) = (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)

    private fun overlaps(p: List<P>, q: List<P>): Boolean {
        for (i in p.indices) for (j in q.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]; val c = q[j]; val d = q[(j + 1) % q.size]
            if (cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0) return true
        }
        return inside(p, q[0]) || inside(q, p[0])
    }

    private fun edgeDistance(poly: List<P>, p: P): Float {
        var best = Float.MAX_VALUE
        for (i in poly.indices) {
            val (x0, y0) = poly[i]
            val (x1, y1) = poly[(i + 1) % poly.size]
            val dx = x1 - x0
            val dy = y1 - y0
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0f) 0f else (((p.first - x0) * dx + (p.second - y0) * dy) / len2).coerceIn(0f, 1f)
            best = minOf(best, hypot(p.first - (x0 + t * dx), p.second - (y0 + t * dy)))
        }
        return best
    }

    /** [poly]'s edges sampled every [step], so a straight seam has points along it and not just at its ends. */
    private fun dense(poly: List<P>, step: Float = 0.003f): List<P> =
        poly.indices.flatMap { i ->
            val (x0, y0) = poly[i]
            val (x1, y1) = poly[(i + 1) % poly.size]
            val n = maxOf(1, (hypot(x1 - x0, y1 - y0) / step).toInt())
            (0 until n).map { k -> (x0 + (x1 - x0) * k / n) to (y0 + (y1 - y0) * k / n) }
        }

    private class Shape(val muscle: Muscle, val s: List<P>)

    private fun shapes(view: List<Region>, vararg muscles: Muscle) =
        view.filter { it.muscle in muscles }.map { Shape(it.muscle, it.points) }

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

    /**
     * Each shape's own horizontal width in [y0, y1], added up: the arm hangs at an angle and the forearm is
     * several slips fanned across it, so the width of the whole is not the width of the muscle.
     */
    private fun List<Shape>.slipWidth(y0: Float, y1: Float): Float = sumOf { sh ->
        val xs = dense(sh.s, 0.001f).filter { it.second in y0..y1 }.map { it.first }
        if (xs.isEmpty()) 0.0 else (xs.max() - xs.min()).toDouble()
    }.toFloat()

    // ---- landmarks, from this body's own outline ----
    private val crotchY: Float by lazy { figure.outline.last().second }
    private val head: Float by lazy { crotchY / 4f }
    private val nippleY: Float by lazy { 2f * head }
    private val navelY: Float by lazy { 3f * head }
    private val pubisY: Float by lazy { crotchY - 0.02f }
    private val midBackY: Float by lazy { 2.5f * head }
    private val acromionY: Float by lazy { figure.outline.filter { it.first > 0.09f }.minOf { it.second } }
    private val clavicleY: Float by lazy { acromionY + 0.006f }
    private val elbowY: Float by lazy { acromionY + 1.2f * head }
    private val wristY: Float by lazy { acromionY + 2.3f * head }

    private val armpit: P by lazy {
        var y = acromionY + 0.04f
        while (y < 0.6f) {
            val xs = crossings(body, y).filter { it > 0.02f }.sorted()
            if (xs.size >= 3) return@lazy y to xs[0]
            y += 0.001f
        }
        error("the outline has no armpit")
    }
    private val armpitY get() = armpit.first
    private val armpitX: Float by lazy { crossings(body, armpitY + 0.004f).filter { it > 0.02f }.sorted()[1] }

    private fun legWidth(y: Float): Float {
        val xs = crossings(body, y).filter { it > 0f }
        return xs.max() - xs.min()
    }

    private fun narrowest(y0: Float, y1: Float): Float = (0..100).map { y0 + (y1 - y0) * it / 100f }.minBy { legWidth(it) }

    private val kneeY: Float by lazy { narrowest(crotchY + 0.15f, crotchY + 0.26f) }
    private val ankleY: Float by lazy { narrowest(0.86f, 0.95f) }

    /** The torso's edge where the arm leaves it; the abs are about as wide as the nipples are apart, well inside. */
    private val torsoHalf: Float get() = armpit.second
    private val absMaxX: Float get() = torsoHalf * 0.6f

    // ---- the whole figure ----

    @Test
    fun `landmarks come out where the canon says`() {
        assertTrue("crotch $crotchY", crotchY in 0.46f..0.55f)
        assertTrue("shoulder line $acromionY should be about 1.2 heads down, head $head", acromionY in (1.0f * head)..(1.4f * head))
        assertTrue("armpit $armpitY should be below the shoulder and above the navel $navelY", armpitY in (acromionY + 0.05f)..navelY)
        assertTrue("torso half-width ${torsoHalf} at the armpit", torsoHalf in 0.05f..0.12f)
        assertTrue("armpit x $armpitX should be near the torso side $torsoHalf", kotlin.math.abs(armpitX - torsoHalf) < 0.03f)
        assertTrue("knee $kneeY between the crotch $crotchY and the ankle $ankleY", kneeY > crotchY + 0.15f && kneeY < ankleY - 0.1f)
        assertTrue("ankle $ankleY", ankleY in 0.86f..0.95f)
        assertTrue("wrist $wristY must be above the knee $kneeY", wristY < crotchY + 0.05f)
    }

    @Test
    fun `each view draws the muscles that belong on it`() {
        assertEquals(
            setOf(TRAPS, NECK, FRONT_DELTS, SIDE_DELTS, UPPER_CHEST, MID_CHEST, LOWER_CHEST, SERRATUS, BICEPS, BRACHIALIS, TRICEPS, FOREARMS, ABS, OBLIQUES, HIP_FLEXORS, QUADS, ADDUCTORS, TIBIALIS, CALVES),
            front.map { it.muscle }.toSet(),
        )
        assertEquals(
            setOf(TRAPS, NECK, RHOMBOIDS, ROTATOR_CUFF, REAR_DELTS, LATS, TRICEPS, FOREARMS, LOWER_BACK, ABDUCTORS, GLUTES, HAMSTRINGS, ADDUCTORS, CALVES),
            back.map { it.muscle }.toSet(),
        )
    }

    @Test
    fun `every muscle the app tracks is on the figure`() {
        assertEquals(Muscle.entries.toSet(), figure.drawn)
        for (m in Muscle.entries) {
            assertTrue("$m has no region on either view", figure.regionsOf(m, FigureView.FRONT).isNotEmpty() || figure.regionsOf(m, FigureView.BACK).isNotEmpty())
        }
    }

    @Test
    fun `every region lies inside the body outline`() {
        for ((name, view) in mapOf("front" to front, "back" to back)) for (r in view) {
            val outside = r.points.filterNot { inside(body, it) }
            assertTrue("$name ${r.muscle} pokes outside the outline at ${outside.firstOrNull()}", outside.isEmpty())
        }
    }

    @Test
    fun `no two regions on a view overlap`() {
        for ((name, view) in mapOf("front" to front, "back" to back)) {
            for (i in view.indices) for (j in i + 1 until view.size) {
                assertTrue("$name: ${view[i].muscle} #$i overlaps ${view[j].muscle} #$j", !overlaps(view[i].points, view[j].points))
            }
        }
    }

    @Test
    fun `the hair sits on the head, inside the outline and off every muscle`() {
        for (v in FigureView.entries) {
            for (hair in figure.hair(v)) {
                assertTrue("$v hair pokes outside the outline", hair.all { inside(body, it) || edgeDistance(body, it) < 0.002f })
                assertTrue("$v hair sits below the nape: ${hair.maxOf { it.second }}", hair.maxOf { it.second } <= 0.12f)
                for (r in figure.regions(v)) assertTrue("$v hair overlaps ${r.muscle}", !overlaps(hair, r.points))
            }
        }
        assertTrue("hair on the front of the head", figure.hair(FigureView.FRONT).isNotEmpty())
        assertTrue("hair on the back of the head", figure.hair(FigureView.BACK).isNotEmpty())
    }

    // ---- front ----

    private val chest = shapes(front, UPPER_CHEST, MID_CHEST, LOWER_CHEST)
    private val abs = shapes(front, ABS)
    private val serratus = shapes(front, SERRATUS)

    /** Where the pec's fibres converge on the arm: the most lateral point of the chest, half-way up its edge. */
    private val chestApex: P by lazy {
        val pts = chest.flatMap { it.s }
        val maxX = pts.maxOf { it.first }
        val near = pts.filter { it.first >= maxX - 0.002f }
        maxX to near.map { it.second }.average().toFloat()
    }

    private fun angleFromApex(p: P) = atan2(p.second - chestApex.second, chestApex.first - p.first)

    @Test
    fun `the pec runs from the clavicle down to the nipple line`() {
        assertEquals("three chest regions", 3, front.count { it.muscle in setOf(UPPER_CHEST, MID_CHEST, LOWER_CHEST) })
        assertTrue("pec top ${chest.top} should sit just under the clavicle $clavicleY", chest.top in clavicleY..(clavicleY + 0.014f))
        assertTrue("pec bottom ${chest.bottom} must reach the nipple line $nippleY", chest.bottom >= nippleY)
        assertTrue("pec bottom ${chest.bottom} is not a belly-sized plate", chest.bottom <= nippleY + 0.025f)
        assertTrue("pec should be at least 0.6 head tall, is ${chest.height}", chest.height >= 0.6f * head)
    }

    @Test
    fun `the pec reaches its point near the armpit`() {
        val tip = chest.flatMap { it.s }.maxBy { it.first }
        assertTrue("pec tip x ${tip.first} should be within 0.02 of the torso's side at the armpit $armpitX", kotlin.math.abs(tip.first - armpitX) <= 0.02f)
        // The source's pec is a rounded plate, not a pointed fan, so its lateral point sits half-way up the
        // plate instead of at the crease: allow it 0.07 above the armpit (the hand-drawn pec: 0.04).
        assertTrue("pec tip y ${chestApex.second} should be above the armpit crease $armpitY, within 0.07", chestApex.second in (armpitY - 0.07f)..armpitY)
    }

    @Test
    fun `the chest is a fan of three bands from the armpit, not horizontal stripes`() {
        val order = listOf(UPPER_CHEST, MID_CHEST, LOWER_CHEST)
        val bands = order.map { m ->
            shapes(front, m).flatMap { it.s }.filter { hypot(it.first - chestApex.first, it.second - chestApex.second) > 0.012f }.map { angleFromApex(it) }
        }
        bands.forEachIndexed { i, a ->
            assertTrue("${order[i]} spans only ${a.max() - a.min()} rad from the armpit", a.max() - a.min() >= 0.2f)
        }
        // Ordered, overlapping only at the seams: clavicular head on top, then the sternal portions down.
        assertTrue("upper ${bands[0].max()} must stay above mid ${bands[1].min()}", bands[0].max() <= bands[1].min() + 0.03f)
        assertTrue("mid ${bands[1].max()} must stay above lower ${bands[2].min()}", bands[1].max() <= bands[2].min() + 0.03f)
        // The seams are rays from the armpit. A horizontal seam seen from the armpit sweeps a wide range of
        // angles; a radial one holds a single angle.
        for ((a, b) in listOf(order[0] to order[1], order[1] to order[2])) {
            val pa = shapes(front, a).single().s
            val pb = shapes(front, b).single().s
            val seam = dense(pa).filter { edgeDistance(pb, it) < 0.003f && hypot(it.first - chestApex.first, it.second - chestApex.second) > 0.02f }
            assertTrue("seam $a/$b has too few points: ${seam.size}", seam.size >= 5)
            val angles = seam.map { angleFromApex(it) }
            assertTrue("seam $a/$b is not radial: it sweeps ${angles.max() - angles.min()} rad", angles.max() - angles.min() <= 0.1f)
        }
    }

    @Test
    fun `the deltoid is a cap with a front head and a side head, tapering onto the arm`() {
        val delt = shapes(front, FRONT_DELTS, SIDE_DELTS)
        val h = delt.height
        assertEquals("one front head, one side head", 2, delt.size)
        assertTrue("delt cap starts at the shoulder line", delt.top <= acromionY + 0.015f)
        assertTrue("delt should run well down the arm: height $h, head $head", h >= 0.45f * head)
        assertTrue("the front head ${shapes(front, FRONT_DELTS).minX} lies medial to the side head ${shapes(front, SIDE_DELTS).minX}", shapes(front, FRONT_DELTS).minX < shapes(front, SIDE_DELTS).minX)
        assertTrue("delt should taper: bottom ${delt.span(delt.bottom - 0.2f * h, delt.bottom)} vs mid ${delt.span(delt.top + 0.4f * h, delt.top + 0.6f * h)}",
            delt.span(delt.bottom - 0.2f * h, delt.bottom) < 0.7f * delt.span(delt.top + 0.4f * h, delt.top + 0.6f * h))
    }

    @Test
    fun `the serratus is finger-like, several slips`() {
        assertTrue("serratus slips: ${serratus.size}", serratus.size >= 3)
    }

    @Test
    fun `the serratus sits beside the pec and the abs, running down past the pec's lower edge`() {
        // The source's slips start at the pec's lower border and run down the ribs beside the abs; the
        // armpit crease is no longer a landmark for them, the nipple line is.
        assertTrue("serratus top ${serratus.top} must be on the pec's lower half, not above the nipple line $nippleY", serratus.top >= nippleY - 0.02f)
        assertTrue("serratus starts ${serratus.minX}, lateral to the abs (${abs.maxX})", serratus.minX > abs.maxX)
        assertTrue("serratus top ${serratus.top} should overlap the pec's lower part (pec bottom ${chest.bottom})", serratus.top < chest.bottom - 0.005f)
        assertTrue("serratus must continue below the pec, not sit entirely in it", serratus.bottom > chest.bottom)
        assertTrue("serratus is not entirely below the pec", serratus.top < chest.bottom)
    }

    @Test
    fun `the abs are a narrow segmented column from the pecs to the pubis`() {
        val chestAtAbs = chest.flatMap { it.s }.filter { it.first <= abs.maxX }.maxOf { it.second }
        assertTrue("abs are ${abs.maxX} wide, limit $absMaxX", abs.maxX <= absMaxX)
        // Measured against the pec over the abs' own width: the pec's outer lobe hangs lower than its inner edge.
        assertTrue("abs begin ${abs.top} just under the pec ${chestAtAbs}", abs.top >= chestAtAbs - 0.012f && abs.top <= chestAtAbs + 0.03f)
        assertTrue("abs end ${abs.bottom} near the pubis $pubisY", abs.bottom in (pubisY - 0.04f)..(pubisY + 0.02f))
    }

    @Test
    fun `the abs are three rows above the navel and a longer lower section`() {
        assertEquals("three rows above the navel", 3, abs.count { it.s.maxOf { p -> p.second } < navelY })
        assertTrue("then a longer lower section across the navel's level and below",
            abs.count { it.s.minOf { p -> p.second } > navelY - 0.03f && it.s.maxOf { p -> p.second } - it.s.minOf { p -> p.second } > 0.07f } == 1)
    }

    @Test
    fun `the obliques are lateral slabs between the ribs and the hip`() {
        val o = shapes(front, OBLIQUES)
        // Their medial corner sits beside the abs' upper rows, where the abs are narrower than at the lower
        // rows, so the extents may overlap by a seam's width while the shapes never do.
        assertTrue("obliques start ${o.minX} lateral to the abs ${abs.maxX}", o.minX > abs.maxX - 0.006f)
        assertTrue("obliques begin at the lower ribs, ${o.top}", o.top >= 2.3f * head)
        assertTrue("obliques end at the iliac crest, ${o.bottom}", o.bottom <= crotchY - 0.03f)
        // Their lower end is the source's iliac crest, which sits at 3.25 to 3.4 heads on these bodies, not at 3.5.
        assertTrue("obliques run below the navel $navelY", o.bottom >= navelY + 0.02f)
    }

    @Test
    fun `the hip flexors lie in the groove at the hip crease`() {
        val hf = shapes(front, HIP_FLEXORS)
        assertTrue("hip flexors sit within a head of the crotch: top ${hf.top}", hf.top >= crotchY - head)
        assertTrue("hip flexors end at the crotch: ${hf.bottom}", hf.bottom <= crotchY)
        assertTrue("hip flexors lie beside the abs, ${hf.minX} vs ${abs.minX}", hf.maxX > abs.maxX)
    }

    @Test
    fun `biceps is a spindle between the shoulder and the elbow`() {
        val b = shapes(front, BICEPS)
        assertTrue("biceps top ${b.top} must be down the arm from the shoulder line $acromionY", b.top > acromionY + 0.05f)
        assertTrue("biceps bottom ${b.bottom} above the elbow $elbowY", b.bottom <= elbowY + 0.012f)
        val h = b.height
        val mid = b.span(b.top + 0.3f * h, b.top + 0.7f * h)
        assertTrue("biceps belly $mid should be wider than its tips", mid >= 1.5f * b.span(b.bottom - 0.1f * h, b.bottom) && mid >= 1.5f * b.span(b.top, b.top + 0.1f * h))
        val bra = shapes(front, BRACHIALIS)
        assertTrue("brachialis stays on the upper arm, ${bra.top}..${bra.bottom}", bra.top > acromionY + 0.05f && bra.bottom <= elbowY + 0.012f)
        assertTrue("brachialis lies outside the biceps", bra.minX >= b.minX && bra.maxX > b.maxX)
    }

    @Test
    fun `the forearm is a cone that ends in tendon above the wrist`() {
        // The source's forearm slips end at the wrist crease, so the bar is the wrist itself plus the width of
        // a seam, where the hand-drawn figure stopped 0.02 short of it.
        for ((name, view) in mapOf("front" to front, "back" to back)) {
            val f = shapes(view, FOREARMS)
            assertTrue("$name forearm ends ${f.bottom}, wrist is $wristY", f.bottom <= wristY + 0.012f)
            assertTrue("$name forearm starts at the elbow, ${f.top}", f.top >= elbowY - 0.02f && f.top <= elbowY + 0.025f)
            // Each slip swells to a belly and narrows to the tendon at the wrist.
            for (slip in f) {
                val top = slip.s.minOf { it.second }
                val bottom = slip.s.maxOf { it.second }
                val widths = (0 until 10).map { k -> listOf(slip).slipWidth(top + (bottom - top) * k / 10f, top + (bottom - top) * (k + 1) / 10f) }
                if (widths.max() < 0.012f) continue // a strap of tendon-thin muscle is narrow all along
                assertTrue("$name forearm slip should narrow to the tendon: ${widths.last()} at the wrist vs ${widths.max()} at its belly", widths.last() < 0.6f * widths.max())
            }
        }
    }

    @Test
    fun `the thigh is adductors then three quad heads between the crotch and the knee`() {
        val q = shapes(front, QUADS)
        assertTrue("vastus medialis, rectus femoris, vastus lateralis", q.size >= 3)
        // The thigh muscles rise from the pelvis, above the crotch: the source starts them 0.07 to 0.1 higher.
        assertTrue("quads begin at the hip, ${q.top}", q.top >= crotchY - head)
        assertTrue("quads end ${q.bottom} above the knee $kneeY", q.bottom <= kneeY)
        val a = shapes(front, ADDUCTORS)
        assertTrue("adductors begin at the groin, ${a.top}", a.top >= crotchY - head)
        assertTrue("adductors taper out above the knee, ${a.bottom}", a.bottom < kneeY - 0.04f)
        assertTrue("adductors are the inner thigh", a.minX < q.minX && a.maxX < q.maxX)
    }

    @Test
    fun `the lower leg is the shin between two calf heads, knee to ankle`() {
        // The shin and calf muscles begin at the joint line, a little above the narrowest row of the knee.
        for (m in listOf(shapes(front, CALVES), shapes(front, TIBIALIS), shapes(back, CALVES))) {
            assertTrue("${m.first().muscle} starts ${m.top} below the knee $kneeY", m.top >= kneeY - 0.04f)
        }
        // The ankle is the narrowest row of a stretch that is nearly flat, so the row itself can sit a hair either side.
        for (m in listOf(shapes(front, CALVES), shapes(front, TIBIALIS))) {
            assertTrue("${m.first().muscle} ends ${m.bottom} above the ankle $ankleY", m.bottom <= ankleY + 0.02f)
        }
        // From behind the soleus runs on into the Achilles, which reaches the heel.
        assertTrue("back calves end ${shapes(back, CALVES).bottom} at the Achilles, ankle $ankleY", shapes(back, CALVES).bottom <= ankleY + 0.07f)
        assertEquals("two calf heads seen from the front", 2, front.count { it.muscle == CALVES })
        assertTrue("two heads and the soleus from behind", back.count { it.muscle == CALVES } >= 3)
    }

    @Test
    fun `the neck sits between the chin and the clavicle, clear of the traps`() {
        val nf = shapes(front, NECK)
        val nb = shapes(back, NECK)
        assertTrue("front neck starts at the chin: ${nf.top} vs $head", nf.top >= head - 0.02f)
        assertTrue("front neck ends at the clavicle: ${nf.bottom} vs $clavicleY", nf.bottom <= clavicleY + 0.02f)
        assertTrue("back neck starts at the skull base: ${nb.top} vs $head", nb.top >= head - 0.04f)
        assertTrue("back neck ends at the shoulder line: ${nb.bottom} vs $clavicleY", nb.bottom <= clavicleY + 0.02f)
        assertTrue("the neck is narrow: ${nf.maxX}, ${nb.maxX}", nf.maxX <= 0.06f && nb.maxX <= 0.06f)
        for ((name, n, t) in listOf(Triple("front", nf, shapes(front, TRAPS)), Triple("back", nb, shapes(back, TRAPS)))) {
            assertTrue("$name neck ${n.top} starts above the traps ${t.top}", n.top < t.top)
            for (a in n) for (b in t) assertTrue("$name neck overlaps the traps", !overlaps(a.s, b.s))
        }
    }

    // ---- back ----

    @Test
    fun `triceps is three heads between the shoulder and the elbow`() {
        val t = shapes(back, TRICEPS)
        assertTrue("triceps top ${t.top} must be down the arm from the shoulder line $acromionY", t.top > acromionY + 0.05f)
        // The triceps runs to the olecranon, the point of the elbow, which hangs about 0.03 below the crease where
        // the biceps stops: the source's two views differ by that much.
        assertTrue("triceps bottom ${t.bottom} above the point of the elbow ${elbowY + 0.04f}", t.bottom <= elbowY + 0.04f)
        assertEquals("long, lateral and medial heads", 3, t.size)
        assertTrue("the triceps also shows on the front arm", shapes(front, TRICEPS).size == 1)
    }

    @Test
    fun `traps are a diamond from the nape down to mid-back, widest at the shoulders`() {
        val t = shapes(back, TRAPS)
        // The source's trapezius is the central diamond only; the shoulder part of the muscle is drawn
        // as the rotator cuff and deltoid pieces beside it, so it does not spread out to the acromion.
        assertTrue("traps start at the shoulder line, ${t.top} vs $acromionY", t.top in (acromionY - 0.02f)..(acromionY + 0.03f))
        assertTrue("traps point down to the shoulder blade, ${t.bottom} vs armpit $armpitY", t.bottom in (armpitY - 0.05f)..(armpitY + 0.08f))
        val h = t.height
        assertTrue("traps are widest at the shoulders: ${t.span(t.top, t.top + 0.2f * h)} vs ${t.span(t.bottom - 0.2f * h, t.bottom)}",
            t.span(t.top, t.top + 0.2f * h) > 2f * t.span(t.bottom - 0.2f * h, t.bottom))
    }

    @Test
    fun `lats are a wing from the armpit to the lower back`() {
        val l = shapes(back, LATS)
        assertTrue("lats begin at the armpit, ${l.top} vs $armpitY", l.top in (armpitY - 0.06f)..(armpitY + 0.02f))
        assertTrue("lats reach the lower back, ${l.bottom} vs $midBackY", l.bottom >= midBackY + 0.01f)
        val h = l.height
        assertTrue("lats taper downward", l.span(l.top, l.top + 0.2f * h) > 1.5f * l.span(l.bottom - 0.2f * h, l.bottom))
    }

    @Test
    fun `scapula muscles sit between the spine and the shoulder`() {
        val r = shapes(back, RHOMBOIDS)
        assertTrue("rhomboids between spine and scapula, x ${r.minX}..${r.maxX}", r.minX >= 0.01f && r.maxX <= 0.06f)
        assertTrue("rhomboids at the upper-mid back, y ${r.top}..${r.bottom}", r.top >= acromionY + 0.03f && r.bottom <= armpitY)
        val c = shapes(back, ROTATOR_CUFF)
        assertTrue("rotator cuff on the scapula, y ${c.top}..${c.bottom}", c.top >= acromionY && c.bottom <= armpitY + 0.03f)
        assertTrue("rotator cuff lateral to the rhomboids, x ${c.minX}", c.minX >= r.maxX - 0.005f)
        val d = shapes(back, REAR_DELTS)
        assertTrue("rear delt caps the shoulder, ${d.top}", d.top <= acromionY + 0.015f)
        assertTrue("rear delt runs well down the arm, ${d.height}", d.height >= 0.45f * head)
    }

    @Test
    fun `erectors run beside the spine`() {
        val pieces = shapes(back, LOWER_BACK)
        val e = listOf(pieces.maxBy { polygonArea(it.s) })
        assertTrue("erectors hug the spine, x ${e.maxX}", e.maxX <= 0.05f)
        assertTrue("erectors run from mid-back to the sacrum, ${pieces.top}..${pieces.bottom}", pieces.top >= midBackY - 0.03f && pieces.bottom >= navelY)
    }

    private fun polygonArea(p: List<P>): Float =
        kotlin.math.abs(p.indices.sumOf { i -> (p[i].first * p[(i + 1) % p.size].second - p[(i + 1) % p.size].first * p[i].second).toDouble() }.toFloat()) / 2f

    @Test
    fun `glutes are a rounded square below the iliac crest, hamstrings the bellies down to the knee`() {
        val g = shapes(back, GLUTES)
        // The source's glute starts at about 3.2 heads, a little above the canon's iliac crest at 3.5.
        assertTrue("glutes begin at the iliac crest, ${g.top}", g.top >= 3.2f * head)
        val aspect = (g.maxX - g.minX) / g.height
        assertTrue("glutes are roughly square, aspect $aspect", aspect in 0.6f..1.4f)
        val m = shapes(back, ABDUCTORS)
        assertTrue("gluteus medius sits above the glute max, ${m.bottom}", m.bottom <= g.top + 0.04f && m.top >= g.top - 0.04f)
        val h = shapes(back, HAMSTRINGS)
        assertTrue("the hamstring bellies: ${h.size}", h.size >= 3)
        assertTrue("hamstrings start under the glutes' top, ${h.top}", h.top >= g.top + 0.05f)
        assertTrue("hamstrings end ${h.bottom} above the knee $kneeY", h.bottom <= kneeY + 0.01f)
    }
}

class MaleFigureAnatomyTest : FigureAnatomyTest() {
    override val figure: BodyFigure get() = BodyFigures.MALE
}
