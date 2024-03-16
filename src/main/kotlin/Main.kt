import kotlinx.browser.document
import kotlinx.browser.window
import org.openrndr.animatable.Animatable
import org.openrndr.animatable.easing.Easing
import org.openrndr.color.ColorRGBa
import org.openrndr.color.rgb
import org.openrndr.math.transforms.buildTransform
import org.openrndr.extra.shapes.rectify.rectified
import org.w3c.dom.svg.SVGElement
import org.w3c.dom.svg.SVGSVGElement

fun main() {
    val text = loadSVG("tessa.svg")
    val shapes = text.findShapes()
    val rectified = shapes.map { it.shape.contours.map { c -> c.rectified() } }

    val svg = document.getElementById("inner-svg") as SVGElement
    val outerSVG = document.getElementById("kotlin-svg") as SVGSVGElement
    fun patchSVG() {
        if (window.innerWidth < window.window.innerHeight) {
            outerSVG.setAttribute("preserveAspectRatio", "xMidYMid slice")
        } else {
            if (outerSVG.attributes.getNamedItem("preserveAspectRatio") != null) {
                outerSVG.attributes.removeNamedItem("preserveAspectRatio")
            }
        }
    }
    patchSVG()
    window.addEventListener("resize", {
        patchSVG()
    }, false)

    class LetterAnimation : Animatable() {
        var length = 0.05
    }

    val letterAnimations = List(5 * 10) { LetterAnimation() }

    class Orchestrator : Animatable() {
        var latch = 0.0
        var mode = 0

        fun update(timeInMs: Double) {
            updateAnimation()
            for (animation in letterAnimations) {
                animation.updateAnimation()
            }
            if (!this.hasAnimations()) {
                when (mode % 3) {
                    0 -> {
                        for ((index, anim) in letterAnimations.withIndex()) {
                            anim.cancel()
                            anim.apply {
                                delay(index * 50L)
                                ::length.animate(1.0, 1000, Easing.CubicInOut)
                                ::length.complete()
                                delay(2000)
                                ::length.animate(0.05, 1000, Easing.CubicInOut)
                            }
                        }
                    }
                    1 -> {
                        for (row in 0 until 10 step 2) {
                            val anim = letterAnimations[row * 5 + row / 2]
                            anim.cancel()
                            anim.apply {
                                delay(row * 250L)
                                ::length.animate(1.0, 1500, Easing.CubicInOut)
                                ::length.complete()
                                delay(2000)
                                ::length.animate(0.05, 1500, Easing.CubicInOut)
                            }
                        }
                    }
                    2 -> {
                        for (row in 0 until 10 step 2) {
                            for (shape in 0 until 5) {
                                val anim = letterAnimations[row * 5 + shape]
                                anim.cancel()
                                anim.apply {
                                    delay((9-row) * 250L)
                                    ::length.animate(1.0, 1000, Easing.CubicInOut)
                                    ::length.complete()
                                    delay(2000)
                                    ::length.animate(0.05, 1000, Easing.CubicInOut)
                                }
                            }
                        }
                    }
                }
                mode++
                ::latch.animate(1.0, 5000)
            }
        }
    }

    val orchestrator = Orchestrator()

    fun generate(millis: Double) {
        val seconds = millis / 1000.0
        svg.innerHTML = ""
        orchestrator.update(millis)
        buildComposition(svg) {
            fill = null
            stroke = rgb("dda9e1")
            strokeWeight = 2.0

            for (i in 0 until 9) {
                val b = buildTransform {
                    translate(210.0, i * 75.0 + 30.0)
                    scale(0.975)
                }
                for ((shapeIndex, shape) in rectified.withIndex()) {
                    val animation = letterAnimations[i * 5 + shapeIndex]
                    val skip = false
                    if (!skip) {
                        for (contour in shape) {
                            val cutStart = seconds * 0.1 + i * 0.1 + shapeIndex * 0.2
                            val cutEnd = seconds * 0.1 + animation.length + i * 0.1 + shapeIndex * 0.2
                            strokeWeight = 3.0
                            stroke =rgb ("dda9e1").mix(ColorRGBa.WHITE, i/15.0).opacify(0.95)
                            fill = null
                            contour(contour.sub(cutStart, cutEnd).transform(b).toSVG())
                        }
                    }
                }
            }
        }
        window.requestAnimationFrame { generate(it) }
    }
    generate(0.0)
}