package party.elias.tunepst

import party.elias.PieceType
import party.elias.Square
import java.text.DecimalFormat
import kotlin.math.min

fun main() {

    val lossFormat = DecimalFormat("#.########")

    val startingPieceValues = intArrayOf(100, 300, 300, 500, 900, 0)

    val pst = IntArray(64 * 2 * 2 * 6) {
        i -> startingPieceValues[i % 6]
    }

    // 0.12418717: 29, 288, 256, 413, 914, 170, 335, 328, 545, 921
    // 0.12404533: 22, 223, 192, 266, 903, 176, 369, 362, 616, 941

    // epoch: 740 lr: 9.286571 loss: 0.12343157

    var lr = 500F

    val batches = Tuner.loadBatches(100, 10000)

    println("loaded ${batches.size} batches")

    val lastFewLosses = ArrayList<Float>()

    val gradientAcc = FloatArray(pst.size)

    var usedBatchCount = 10

    for (epoch in 0..500) {
        var trainingLoss = 0F
        for (batchIndex in 1..<usedBatchCount) {
            val (g, batchLoss) = Tuner.gradient(batches[batchIndex], pst)

            trainingLoss += batchLoss / (usedBatchCount - 1)

            for (i in 0..<gradientAcc.size) {
                gradientAcc[i] += g[i] * lr

                if (gradientAcc[i] > 1) {
                    gradientAcc[i] -= 1
                    pst[i] += 1
                } else if (gradientAcc[i] < -1) {
                    gradientAcc[i] += 1
                    pst[i] -= 1
                }
            }
        }

        if (epoch % 10 == 0) {
            if (epoch % 100 == 0) printPst(pst)

            val testingLoss = Tuner.loss(batches[0], pst)
            val lossTrend = testingLoss - lastFewLosses.average()

            println("epoch: $epoch used batches: $usedBatchCount lr: $lr loss: ${lossFormat.format(trainingLoss)} testing loss: ${lossFormat.format(testingLoss)} (${lossFormat.format(lossTrend)})")

            lastFewLosses.add(testingLoss)
            if (lastFewLosses.size > 5) {
                lastFewLosses.removeFirst()
            }

            if (epoch % 100 == 0) usedBatchCount = min(usedBatchCount * 2, batches.size)

            if (lossTrend > -0.000001) {
                println("plateauing loss, stopping tuning")
                break
            }
        }

        // lr *= 0.99F
    }

    printPst(pst)

    for (i in 0..<pst.size) {
        print("${pst[i]}, ")
    }
    println()

    val testingLoss = Tuner.loss(batches[0], pst)
    println("final results: testing loss: $testingLoss")

}

fun printPst(pst: IntArray) {
    for (piece in 0..5) {
        val pieceName = PieceType(piece).name

        println("midgame king half $pieceName table")
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                print("%6d".format(pst[Square(rank, file).value * 2 * 2 * 6 + piece]))
            }
            println()
        }

        println()

        println("endgame king half $pieceName table")
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                print("%6d".format(pst[Square(rank, file).value * 2 * 2 * 6 + 6 + piece]))
            }
            println()
        }

        println()

        println("midgame non king half $pieceName table")
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                print("%6d".format(pst[Square(rank, file).value * 2 * 2 * 6 + 12 + piece]))
            }
            println()
        }

        println()

        println("endgame non king half $pieceName table")
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                print("%6d".format(pst[Square(rank, file).value * 2 * 2 * 6 + 12 + 6 + piece]))
            }
            println()
        }

        println()
    }
}