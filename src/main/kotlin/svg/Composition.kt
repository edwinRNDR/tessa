import kotlinx.browser.document
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.Shape
import org.openrndr.shape.ShapeContour
import org.w3c.css.masking.SVGClipPathElement
import org.w3c.dom.Element
import org.w3c.dom.get
import org.w3c.dom.svg.*

val svgns = "http://www.w3.org/2000/svg"

public external abstract class SVGAnimateElement : Element {
    fun beginElement()
    fun beginElementAt(at: Double)
}

public external abstract class SVGAnimateTransformElement : Element {
    fun beginElement()
    fun beginElementAt(at: Double)
}

fun ColorRGBa.toWeb(): String {
    return "rgba(${(r * 255).toInt()},${(g * 255).toInt()},${(b * 255).toInt()}, $a)"
}

fun ShapeContour.toSVG():String {

    val sb= StringBuilder()
    if (this.empty) {
        return ""
    } else {

        val first = segments.first().start
        sb.append("M ${first.x} ${first.y}")
        segments.forEach {
            when(it.control.size) {
                0 -> sb.append("L ${it.end.x} ${it.end.y}")
                1 -> sb.append("Q ${it.control[0].x} ${it.control[0].y} ${it.end.x} ${it.end.y}")
                2 -> sb.append("C ${it.control[0].x} ${it.control[0].y} ${it.control[1].x} ${it.control[1].y} ${it.end.x} ${it.end.y}")
            }
        }
        if (closed) {
            sb.append("Z")
        }
        return sb.toString()
    }

}


//
//
//class ContourBuilder {
//    var path = ""
//    var cursor = Vector2(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
//    fun moveTo(v: Vector2) {
//        path += "M${v.x} ${v.y}"
//    }
//
//    fun lineTo(v: Vector2) {
//        path += "L${v.x} ${v.y}"
//    }
//
//    fun curveTo(c0: Vector2, c1: Vector2, v: Vector2) {
//        path += "C${c0.x} ${c0.y} ${c1.x} ${c1.y} ${v.x} ${v.y}"
//    }
//
//    fun curveTo(c: Vector2, v: Vector2) {
//        path += "Q${c.x} ${c.y} ${v.x} ${v.y}"
//    }
//
//    fun close() {
//        path += "Z"
//    }
//
//    private fun Boolean.toInt():Int {
//        if (this) return 1 else return 0
//    }
//
//    fun arcTo(rx: Double, ry:Double, rotation:Double, largeArc:Boolean, sweepFlag:Boolean, x:Double, y:Double) {
//        path += "A $rx $ry $rotation ${largeArc.toInt()} ${sweepFlag.toInt()} $x $y"
//    }
//
//
//    fun build(): String {
//        return path
//    }
//}

sealed class Paint

class CompositionDrawer(val svg: SVGElement) {
    var cursor = svg
    var fill: Any? = ColorRGBa(1.0, 0.0, 0.0, 1.0)
    var stroke: Any? = ColorRGBa(0.0, 0.0, 0.0, 1.0)
    var strokeWeight = 1.0
    private var uid = 0

    private var defs: SVGDefsElement? = null
        get() {
            if (field == null) {
                val newDefs = document.createElementNS(svgns, "defs") as SVGDefsElement
                svg.appendChild(newDefs)
                field = newDefs
            }
            return field
        }

    private fun setDrawAttributes(element: SVGElement) {
        when (val f = fill) {
            is ColorRGBa -> element.setAttribute("fill", f.toWeb())
//            is ColorOKLABa -> element.setAttribute("fill", f.toRGBa().toWeb())
//            is ColorOKLCHa -> element.setAttribute("fill", f.toRGBa().toWeb())
            is SVGGradientElement -> element.setAttribute("fill", "url('#${f.id}')")
            null -> element.setAttribute("fill", "none")
        }

        when (val f = stroke) {
            is ColorRGBa -> element.setAttribute("stroke", f.toWeb())
//            is ColorOKLABa -> element.setAttribute("stroke", f.toRGBa().toWeb())
//            is ColorOKLCHa -> element.setAttribute("stroke", f.toRGBa().toWeb())
            is SVGGradientElement -> element.setAttribute("stroke", "url('#${f.id}')")
            null -> element.setAttribute("stroke", "none")
        }

        element.setAttribute("stroke-width", strokeWeight.toString())
        if (clip != null) {
            element.setAttribute("clip-path", "url(#${clip?.id})")
        }
    }

    private var cursorStack = mutableListOf(cursor)
    private var clipStack = mutableListOf<SVGClipPathElement?>(null)
    private var clip: SVGClipPathElement? = null

    class GradientBuilder(val gradient: SVGGradientElement) {
//        fun stop(offset: Int, color: ColorOKLCHa) {
//            stop(offset, color.toOKLABa())
//        }
//
//        fun stop(offset: Int, color: ColorOKLABa) {
//            stop(offset, color.toRGBa())
//        }

        fun stop(offset: Int, color: ColorRGBa) {
            val stop = document.createElementNS(svgns, "stop") as SVGStopElement
            stop.setAttribute("offset", "$offset%")
            stop.setAttribute("stop-color", color.toWeb())
            gradient.appendChild(stop)
        }
    }

    fun defineLinearGradient(
        x1: String, y1: String, x2: String, y2: String,
        id: String = "lg$uid",
        f: GradientBuilder.() -> Unit
    ): SVGLinearGradientElement {
        val grad = document.createElementNS(svgns, "linearGradient") as SVGLinearGradientElement

        grad.setAttribute("x1", x1)
        grad.setAttribute("x2", x2)
        grad.setAttribute("y1", y1)
        grad.setAttribute("y2", y2)
        grad.id = id
        uid++
        val builder = GradientBuilder(grad)
        builder.f()
        defs?.appendChild(grad)
        return grad
    }

    fun defineRadialGradient(
        id: String = "lg$uid",
        f: GradientBuilder.() -> Unit
    ): SVGRadialGradientElement {
        val grad = document.createElementNS(svgns, "radialGradient") as SVGRadialGradientElement

        grad.id = id
        uid++
        val builder = GradientBuilder(grad)
        builder.f()
        defs?.appendChild(grad)
        return grad
    }


    fun defineClipPath(id: String = "clip$uid", f: () -> Unit): SVGClipPathElement {
        val clipPath = document.createElementNS(svgns, "clipPath") as SVGClipPathElement
        clipPath.id = id
        uid++
        cursorStack.add(cursor)
        cursor = clipPath
        f()
        cursor = cursorStack.removeLast()
        defs?.appendChild(clipPath)
        return clipPath
    }

    fun SVGClipPathElement.clip(f: () -> Unit) {
        clipStack.add(clip)
        clip = this
        f()
        clip = clipStack.removeLast()
    }

    fun SVGElement.animateRotation(
        delay: Double,
        duration: Double,
        startDegrees: Double,
        endDegrees: Double,
        pivot: Vector2
    ): SVGAnimateElement? {
        val animate = document.createElementNS(svgns, "animateTransform") as SVGAnimateTransformElement
        animate.setAttribute("attributeName", "transform")
        animate.setAttribute("attributeType", "XML")
        animate.setAttribute("type", "rotate")
        animate.setAttribute("from", "$startDegrees ${pivot.x} ${pivot.y}")
        animate.setAttribute("to", "$endDegrees ${pivot.x} ${pivot.y}")
        animate.setAttribute("dur", "${duration}s")
        animate.setAttribute("begin", "indefinite")
        console.log(animate)

        animate.setAttribute("data-delay", delay.toString())
        this.appendChild(animate)
        //return animate
        return null
    }

    fun SVGElement.animate(
        attribute: String, delay: Double, duration: Double,
        values: List<Double>, keyTimes: List<Double>,
        keySplines: List<List<Double>> = emptyList<List<Double>>(),

        begin: String = "indefinite"
    ): SVGAnimateElement {
        val animate = document.createElementNS(svgns, "animate") as SVGAnimateElement
        animate.setAttribute("attributeName", attribute)

//        val delayedValues = if (delay > 0.0) values.take(1) + values else values
//        val delayedKeyTimes = if (delay > 0.0) listOf(0.0) + keyTimes.map {
//            ((it * duration) + delay) / (delay + duration)
//        } else keyTimes

        animate.setAttribute("values", values.joinToString(";"))
        animate.setAttribute("keyTimes", keyTimes.joinToString(";"))
        animate.setAttribute("begin", begin)
        animate.setAttribute("dur", "${duration + delay}s")
        animate.setAttribute("fill", "freeze")
        animate.setAttribute("data-delay", delay.toString())

        if (keySplines.isNotEmpty()) {
            animate.setAttribute("calcMode", "spline")
            animate.setAttribute("keySplines", keySplines.joinToString(";") { it.joinToString(" ") })
        }
        this.appendChild(animate)
        return animate
    }

    fun text(text: String, anchor: String = "middle", position:Vector2, class_:String? = null ): SVGTextElement {
        val textElement = document.createElementNS(svgns, "text") as SVGTextElement
        textElement.setAttribute("x", position.x.toString())
        textElement.setAttribute("y", position.y.toString())
        textElement.setAttribute("text-anchor", anchor)
        class_?.let {
            textElement.setAttribute("class", it)

        }
        setDrawAttributes(textElement)
        textElement.textContent = text
        cursor.appendChild(textElement)
        return textElement

    }

    fun group(clipPath: SVGClipPathElement? = null, f: () -> Unit) : SVGElement {
        val group = document.createElementNS(svgns, "g") as SVGElement


        cursorStack.add(cursor)
        cursor = group
        if (clipPath != null) {
            group.setAttribute("clip-path", "url(#${clipPath.id})")
        }
        f()
        cursor = cursorStack.removeLast()
        cursor.appendChild(group)
        return group

    }

    fun circle(x: Double, y: Double, radius: Double): SVGCircleElement {
        val circle = document.createElementNS(svgns, "circle") as SVGCircleElement
        circle.setAttribute("cx", x.toString())
        circle.setAttribute("cy", y.toString())
        circle.setAttribute("r", radius.toString())
        setDrawAttributes(circle)
        cursor.appendChild(circle)
        return circle
    }

    fun rectangle(x: Double, y: Double, width: Double, height: Double): SVGRectElement {
        val rect = document.createElementNS(svgns, "rect") as SVGRectElement
        rect.setAttribute("x", x.toString())
        rect.setAttribute("y", y.toString())
        rect.setAttribute("width", width.toString())
        rect.setAttribute("height", height.toString())
        setDrawAttributes(rect)
        cursor.appendChild(rect)
        return rect
    }

    fun contour(path: String): SVGPathElement {
        val pathElement = document.createElementNS(svgns, "path") as SVGPathElement
        pathElement.setAttribute("d", path)
        setDrawAttributes(pathElement)
        cursor.appendChild(pathElement)
        return pathElement
    }

    fun shape(shape: Shape): SVGPathElement {
        val pathElement = document.createElementNS(svgns, "path") as SVGPathElement
        pathElement.setAttribute("d",
            shape.contours.joinToString(" ") { it.toSVG() })
        setDrawAttributes(pathElement)
        cursor.appendChild(pathElement)
        return pathElement
    }
}

fun buildComposition(svg: SVGElement, f: CompositionDrawer.() -> Unit) {
    val drawer = CompositionDrawer(svg)
    drawer.f()
}

//fun buildContour(f: ContourBuilder.() -> Unit): String {
//    val builder = ContourBuilder()
//    builder.f()
//    return builder.build()
//}

fun SVGElement.beginAnimation() {
    val animations = this.getElementsByTagNameNS(svgns, "animate")
    for (i in 0 until animations.length) {
        val delay = animations.get(i)?.getAttribute("data-delay")?.toDoubleOrNull() ?: 0.0

        (animations.get(i) as SVGAnimateElement).beginElementAt(delay)
    }

    val transformAnimations = this.getElementsByTagNameNS(svgns, "animateTransform")
    for (i in 0 until transformAnimations.length) {
        val delay = transformAnimations.get(i)?.getAttribute("data-delay")?.toDoubleOrNull() ?: 0.0
        (transformAnimations.get(i) as SVGAnimateTransformElement).beginElementAt(delay)
    }
}