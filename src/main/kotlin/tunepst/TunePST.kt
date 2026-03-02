package party.elias.tunepst

import party.elias.Eval
import party.elias.PieceType
import party.elias.Square
import java.text.DecimalFormat

fun main() {

    val lossFormat = DecimalFormat("#.########")

    val startingPieceValues = intArrayOf(100, 300, 300, 500, 900, 0)

    val parameters = IntArray(Eval.PST_SIZE) {
        i -> if (i in Eval.PST_INDEX..<(Eval.PST_INDEX + Eval.PST_SIZE)) startingPieceValues[i % 6] else 0
    }

    // 0.12418717: 29, 288, 256, 413, 914, 170, 335, 328, 545, 921
    // 0.12404533: 22, 223, 192, 266, 903, 176, 369, 362, 616, 941

    // epoch: 740 lr: 9.286571 loss: 0.12343157

    var lr = 750F

    val batchCount = 24
    val batches = Tuner.loadBatches(batchCount, 50000)
    val gradientAcc = FloatArray(parameters.size)

    println("loaded ${batches.size} batches")

    val lastFewLosses = ArrayList<Float>()

    for (epoch in 0..1000) {

        var trainingLoss = 0F
        for (batchIndex in 1..<batchCount) {
            val (g, batchLoss) = Tuner.gradient(batches[batchIndex], parameters)

            trainingLoss += batchLoss / (batchCount - 1)

            for (i in 0..<gradientAcc.size) {
                gradientAcc[i] += g[i] * lr

                parameters[i] += gradientAcc[i].toInt()
                gradientAcc[i] -= gradientAcc[i].toInt()
            }
        }

        if (epoch % 10 == 0) {
            if (epoch % 100 == 0) {
                printPst(parameters)
                //printKingSafetyParameters(parameters)
            }

            val testingLoss = Tuner.loss(batches[0], parameters)
            val lossTrend = testingLoss - lastFewLosses.average()

            println("epoch: $epoch lr: $lr loss: ${lossFormat.format(trainingLoss)} testing loss: ${lossFormat.format(testingLoss)} (${lossFormat.format(lossTrend)})")

            lastFewLosses.add(testingLoss)
            if (lastFewLosses.size > 5) {
                lastFewLosses.removeFirst()
            }
        }

        // lr *= 0.99F
    }

    printPst(parameters)
    //printKingSafetyParameters(parameters)

    for (i in 0..<parameters.size) {
        print("${parameters[i]}, ")
    }
    println()

    val testingLoss = Tuner.loss(batches[0], parameters)
    println("final results: testing loss: $testingLoss")

}

fun printPst(parameters: IntArray) {
    for (piece in 0..5) {
        val pieceName = PieceType(piece).name

        println("midgame king half $pieceName table")
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                print("%6d".format(parameters[Eval.PST_INDEX + Square(rank, file).value * 2 * 2 * 6 + piece]))
            }
            println()
        }

        println()

        println("endgame king half $pieceName table")
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                print("%6d".format(parameters[Eval.PST_INDEX + Square(rank, file).value * 2 * 2 * 6 + 6 + piece]))
            }
            println()
        }

        println()

        println("midgame non king half $pieceName table")
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                print("%6d".format(parameters[Eval.PST_INDEX + Square(rank, file).value * 2 * 2 * 6 + 12 + piece]))
            }
            println()
        }

        println()

        println("endgame non king half $pieceName table")
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                print("%6d".format(parameters[Eval.PST_INDEX + Square(rank, file).value * 2 * 2 * 6 + 12 + 6 + piece]))
            }
            println()
        }

        println()
    }
}

fun printKingSafetyParameters(parameters: IntArray) {
    println("movement: ${parameters[Eval.KING_SAFETY_INDEX]} bishop danger: ${parameters[Eval.KING_SAFETY_INDEX + 1]} rook danger: ${parameters[Eval.KING_SAFETY_INDEX + 2]}")
}