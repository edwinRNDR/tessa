import kotlinx.browser.document
import org.openrndr.extra.composition.Composition
import org.openrndr.extra.composition.CompositionNode
import org.openrndr.extra.composition.GroupNode
import org.openrndr.extra.composition.ShapeNode
import org.openrndr.math.Vector2
import org.openrndr.math.YPolarity
import org.openrndr.shape.Segment
import org.openrndr.shape.Shape
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contour

import org.w3c.dom.get
import org.w3c.dom.svg.SVGElement
import org.w3c.dom.svg.SVGPathElement
import org.w3c.dom.svg.SVGSVGElement
import org.w3c.xhr.XMLHttpRequest

fun loadSVG(url: String): Composition {
    val svg = document.createElementNS("http://www.w3.org/2000/svg", "svg") as SVGSVGElement
    var resultComposition: Composition? = null
    val xhr = XMLHttpRequest()
    xhr.open("GET", url, false)
    xhr.overrideMimeType("image/svg+xml");
    xhr.addEventListener("load", {
        xhr.responseXML?.documentElement?.attributes?.let {
            for (i in 0 until it.length) {
                val name = it[i]?.name ?: error("no name")
                val value = it[i]?.value ?: error("no value")
                svg.setAttribute(name, value)
            }

        }
        svg.innerHTML = xhr.responseXML?.documentElement?.innerHTML ?: error("no svg data")
        val root = GroupNode()
        val composition = Composition(root)
        svg.toComposition(root)

        resultComposition = composition
    }, false)
    xhr.send()
    svg.remove()
    return resultComposition ?: error("no composition")
}

fun SVGElement.toComposition(node: CompositionNode) {
    when (this.tagName) {
        "svg", "g" -> {
            val cg = GroupNode()
            for (child in 0 until children.length) {
                (children[child] as SVGElement).toComposition(cg)
            }
            (node as GroupNode).children.add(cg)
        }
        "path" -> {
            val path = (this as SVGPathElement).getAttribute("d") ?: error("no path")
            val shape = SVGPath.fromSVGPathString(path).shape()
            val cs = ShapeNode(shape)
            (node as GroupNode).children.add(cs)
        }
        else -> error("unsupported node")
    }
}

internal fun Double.toBoolean() = this.toInt() == 1
internal class Command(val op: String, vararg val operands: Double) {
    fun vector(i0: Int, i1: Int): Vector2 {
        val x = if (i0 == -1) 0.0 else operands[i0]
        val y = if (i1 == -1) 0.0 else operands[i1]
        return Vector2(x, y)
    }

    fun vectors(): List<Vector2> = (0 until operands.size / 2).map { Vector2(operands[it * 2], operands[it * 2 + 1]) }
}

internal class SVGPath {
    val commands = mutableListOf<Command>()

    companion object {
        fun fromSVGPathString(svgPath: String): SVGPath {
            val path = SVGPath()
            val rawCommands = svgPath.split("(?=[MmZzLlHhVvCcSsQqTtAa])".toRegex()).dropLastWhile { it.isEmpty() }
            val numbers = Regex("[-+]?[0-9]*[.]?[0-9]+(?:[eE][-+]?[0-9]+)?")
            val arcOpReg = Regex("[aA]")

            for (rawCommand in rawCommands) {
                if (rawCommand.isNotEmpty()) {
                    // Special case for arcTo command where the "numbers" RegExp breaks
                    if (arcOpReg.matches(rawCommand[0].toString())) {
                        parseArcCommand(rawCommand.substring(1)).forEach {
                            val operands = it.map { operand -> operand.toDouble() }
                            path.commands.add(Command(rawCommand[0].toString(), *(operands.toDoubleArray())))
                        }
                    } else {
                        val numberMatches = numbers.findAll(rawCommand)
                        val operands = mutableListOf<Double>()
                        for (i in numberMatches) {
                            operands.add(i.value.toDouble())
                        }
                        path.commands.add(Command(rawCommand[0].toString(), *(operands.toDoubleArray())))
                    }
                }
            }
            return path
        }
    }

    private fun compounds(): List<SVGPath> {
        val compounds = mutableListOf<SVGPath>()
        val compoundIndices = mutableListOf<Int>()

        commands.forEachIndexed { index, it ->
            if (it.op == "M" || it.op == "m") {
                compoundIndices.add(index)
            }
        }

        compoundIndices.forEachIndexed { index, _ ->
            val cs = compoundIndices[index]
            val ce = if (index + 1 < compoundIndices.size) (compoundIndices[index + 1]) else commands.size

            val path = SVGPath()
            path.commands.addAll(commands.subList(cs, ce))


            compounds.add(path)
        }
        return compounds
    }

    fun shape(): Shape {
        var cursor = Vector2(0.0, 0.0)
        var anchor = cursor.copy()
        var relativeControl = Vector2(0.0, 0.0)

        val contours = compounds().map { compound ->
            val segments = mutableListOf<Segment>()
            var closed = false
            compound.commands.forEach { command ->
                when (command.op) {
                    "a", "A" -> {
                        command.operands.let {
                            val rx = it[0]
                            val ry = it[1]
                            val xAxisRot = it[2]
                            val largeArcFlag = it[3].toBoolean()
                            val sweepFlag = it[4].toBoolean()

                            var end = Vector2(it[5], it[6])

                            if (command.op == "a") end += cursor

                            val c = contour {
                                moveTo(cursor)
                                arcTo(rx, ry, xAxisRot, largeArcFlag, sweepFlag, end)
                            }.segments

                            segments += c
                            cursor = end
                        }
                    }
                    "M" -> {
                        cursor = command.vector(0, 1)
                        anchor = cursor

                        val allPoints = command.vectors()

                        for (i in 1 until allPoints.size) {
                            val point = allPoints[i]
                            segments += Segment(cursor, point)
                            cursor = point
                        }
                    }
                    "m" -> {
                        val allPoints = command.vectors()
                        cursor += command.vector(0, 1)
                        anchor = cursor

                        for (i in 1 until allPoints.size) {
                            val point = allPoints[i]
                            segments += Segment(cursor, cursor + point)
                            cursor += point
                        }
                    }
                    "L" -> {
                        val allPoints = command.vectors()

                        for (point in allPoints) {
                            segments += Segment(cursor, point)
                            cursor = point
                        }
                    }
                    "l" -> {
                        val allPoints = command.vectors()

                        for (point in allPoints) {
                            val target = cursor + point
                            segments += Segment(cursor, target)
                            cursor = target
                        }
                    }
                    "h" -> {
                        for (operand in command.operands) {
                            val startCursor = cursor
                            val target = startCursor + Vector2(operand, 0.0)
                            segments += Segment(cursor, target)
                            cursor = target
                        }
                    }
                    "H" -> {
                        for (operand in command.operands) {
                            val target = Vector2(operand, cursor.y)
                            segments += Segment(cursor, target)
                            cursor = target
                        }
                    }
                    "v" -> {
                        for (operand in command.operands) {
                            val target = cursor + Vector2(0.0, operand)
                            segments += Segment(cursor, target)
                            cursor = target
                        }
                    }
                    "V" -> {
                        for (operand in command.operands) {
                            val target = Vector2(cursor.x, operand)
                            segments += Segment(cursor, target)
                            cursor = target
                        }
                    }
                    "C" -> {
                        val allPoints = command.vectors()
                        allPoints.windowed(3, 3).forEach { points ->
                            segments += Segment(cursor, points[0], points[1], points[2])
                            cursor = points[2]
                            relativeControl = points[1] - points[2]
                        }
                    }
                    "c" -> {
                        val allPoints = command.vectors()
                        allPoints.windowed(3, 3).forEach { points ->
                            segments += Segment(cursor, cursor + points[0], cursor + points[1], cursor.plus(points[2]))
                            relativeControl = (cursor + points[1]) - (cursor + points[2])
                            cursor += points[2]
                        }
                    }
                    "Q" -> {
                        val allPoints = command.vectors()
                        if ((allPoints.size) % 2 != 0) {
                            error("invalid number of operands for Q-op (operands=${allPoints.size})")
                        }
                        for (c in 0 until allPoints.size / 2) {
                            val points = allPoints.subList(c * 2, c * 2 + 2)
                            segments += Segment(cursor, points[0], points[1])
                            cursor = points[1]
                            relativeControl = points[0] - points[1]
                        }
                    }
                    "q" -> {
                        val allPoints = command.vectors()
                        if ((allPoints.size) % 2 != 0) {
                            error("invalid number of operands for q-op (operands=${allPoints.size})")
                        }
                        for (c in 0 until allPoints.size / 2) {
                            val points = allPoints.subList(c * 2, c * 2 + 2)
                            val target = cursor + points[1]
                            segments += Segment(cursor, cursor + points[0], target)
                            relativeControl = (cursor + points[0]) - (cursor + points[1])
                            cursor = target
                        }
                    }
                    "s" -> {
                        val reflected = relativeControl * -1.0
                        val cp0 = cursor + reflected
                        val cp1 = cursor + command.vector(0, 1)
                        val target = cursor + command.vector(2, 3)
                        segments += Segment(cursor, cp0, cp1, target)
                        cursor = target
                        relativeControl = cp1 - target
                    }
                    "S" -> {
                        val reflected = relativeControl * -1.0
                        val cp0 = cursor + reflected
                        val cp1 = command.vector(0, 1)
                        val target = command.vector(2, 3)
                        segments += Segment(cursor, cp0, cp1, target)
                        cursor = target
                        relativeControl = cp1 - target
                    }
                    "Z", "z" -> {
                        if ((cursor - anchor).length >= 0.001) {
                            segments += Segment(cursor, anchor)
                        }
                        closed = true
                    }
                    else -> {
                        error("unsupported op: ${command.op}, is this a TinySVG 1.x document?")
                    }
                }
            }
            ShapeContour(segments, closed, YPolarity.CW_NEGATIVE_Y)
        }
        return Shape(contours)
    }

}

internal fun parseArcCommand(p: String): List<List<String>> {
    val sepReg = Regex(",|\\s")
    val boolReg = Regex("[01]")

    var cursor = 0
    var group = ""
    val groups = mutableListOf<String>()
    val commands = mutableListOf<List<String>>()

    while (cursor <= p.lastIndex) {
        val token = p[cursor].toString()
        if (sepReg.matches(token)) {
            if (group.isNotEmpty()) {
                groups.add(group)
            }
            group = ""
        } else {
            group += token

            if ((boolReg.matches(token) && (groups.size in 3..5)) || cursor == p.lastIndex) {
                if (group.isNotEmpty()) {
                    groups.add(group)
                }

                group = ""
            }
        }

        if (groups.size == 7) {
            commands.add(groups.toList())
            groups.clear()
            group = ""
        }

        cursor++
    }

    return commands
}