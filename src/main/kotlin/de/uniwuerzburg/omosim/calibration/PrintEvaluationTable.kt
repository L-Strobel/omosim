package de.uniwuerzburg.omosim.calibration

import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.math.abs

/**
 * Print evaluation table
 *
 * @param index (Multi-)index of the table. Keys = Names for header, values = Columns of index
 * @param measurements Measured values (Column)
 * @param baseline Not calibrated values (Column)
 * @param calibrated Calibrated values (Column)
 * @param metricNames Names of optional metrics printed at the top of the table
 * @param metricBase Metric values for baseline
 * @param metricCal Metric values for calibrated model
 */
fun printEvaluationTable(
    index: Map<String, List<String>>,
    measurements: List<Double>,
    baseline: List<Double>,
    calibrated: List<Double>,
    metricNames: List<String> = listOf(),
    metricBase: List<Double> = listOf(),
    metricCal: List<Double> = listOf(),
) {
    require(measurements.size == baseline.size) {"All columns must be equal in length!"}
    require(baseline.size == calibrated.size) {"All columns must be equal in length!"}
    require(index.values.all { it.size == measurements.size }) {"All columns must be equal in length!"}
    require(metricNames.size == metricBase.size) {"Metric names must be equal to the supplied values!"}
    require(metricBase.size == metricCal.size) {"Number of metrics must be equal for baseline and calibration!"}

    val t = Terminal()
    t.println(table {
        borderType = BorderType.SQUARE_DOUBLE_SECTION_SEPARATOR
        captionTop("Traffic Count Comparison:", align = TextAlign.LEFT)
        header {
            row {
                val header = index.keys.toList() + listOf("Measured", "Sim. Base", "Sim. Calibrated")
                cellsFrom(header)
                cellBorders = Borders.TOP
            }
            for (i in metricNames.indices) {
                row {
                    val name = metricNames[i]
                    val mBase = metricBase[i]
                    val mCal = metricCal[i]

                    // Choose color based on whether metric improved through calibration
                    val styleSSEBase = TextColors.blue + TextStyles.bold
                    val styleSSECal = getImprovementIndicationColor(0.0,mBase,mCal) +
                            TextStyles.bold

                    cells(
                        "",
                        "",
                        "",
                        styleSSEBase("$name  %.4g".format(mBase)),
                        styleSSECal("$name  %.4g".format(mCal))
                    )
                    cellBorders = Borders.NONE
                    align = TextAlign.RIGHT
                }
            }
        }
        body {
            // Column styles
            column(0) {
                cellBorders = Borders.TOP_BOTTOM
            }
            column(1) {
                cellBorders = Borders.TOP_RIGHT_BOTTOM
            }
            column(2) {
                cellBorders = Borders.TOP_BOTTOM
                align = TextAlign.RIGHT
            }
            column(3) {
                cellBorders = Borders.TOP_BOTTOM
                align = TextAlign.RIGHT
                style = TextColors.blue
            }
            column(4) {
                cellBorders = Borders.TOP_BOTTOM
                align = TextAlign.RIGHT
            }

            // Rows
            for (i in measurements.indices) {
                val calibratedColor = getImprovementIndicationColor(measurements[i],baseline[i],calibrated[i])

                val rowValues = mutableListOf<Any>()
                for (k in index.keys) {
                    rowValues.add(index[k]!![i])
                }
                rowValues.add("%.2f".format(measurements[i]))
                rowValues.add("%.2f".format(baseline[i]))
                rowValues.add(calibratedColor("%.2f".format(calibrated[i])))
                rowFrom(rowValues)
            }
        }
    })
}

/**
 * Green if calibration improved value, red if it worsened the value, and white if the value did not change.
 */
private fun getImprovementIndicationColor(
    valueTarget: Double,
    valueBase: Double,
    valueCal: Double
) : TextColors {
    val diffCal = abs(valueTarget - valueCal)
    val diffBase = abs(valueTarget - valueBase)

    return if (diffCal < diffBase) {
        TextColors.green
    } else if (diffCal == diffBase) {
        TextColors.white
    } else {
        TextColors.red
    }
}