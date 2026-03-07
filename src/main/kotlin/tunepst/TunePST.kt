package party.elias.tunepst

import party.elias.Eval
import party.elias.PieceType
import party.elias.Square
import java.text.DecimalFormat

fun main() {

    val lossFormat = DecimalFormat("#.########")

    val startingPieceValues = intArrayOf(100, 300, 300, 500, 900, 0)

    val parameters = IntArray(Eval.PST_SIZE + Eval.PASSED_SIZE + Eval.BISHOP_PAIR_SIZE) {
        i -> if (i in Eval.PST_INDEX..<(Eval.PST_INDEX + Eval.PST_SIZE)) startingPieceValues[i / (2 * 2 * 64)] else 0
    }

    // 0.12418717: 29, 288, 256, 413, 914, 170, 335, 328, 545, 921
    // 0.12404533: 22, 223, 192, 266, 903, 176, 369, 362, 616, 941

    // epoch: 740 lr: 9.286571 loss: 0.12343157

    var lr = 750F

    val batchCount = 20
    val batchSize = 50000
    var megaBatchIndex = 0
    var batches: ArrayList<Array<Tuner.Sample>> = ArrayList()
    val gradientAcc = FloatArray(parameters.size)

    val testBatch = Tuner.loadBatches(0, 1, batchSize)[0]

    val lastFewLosses = ArrayList<Float>()

    for (epoch in 0..1000) {
        if (epoch % 100 == 0) {
            batches = Tuner.loadBatches(megaBatchIndex++ * batchCount * batchSize + batchSize, batchCount, batchSize)
            println("loaded ${batches.size} batches")
        }

        var trainingLoss = 0F
        for (batchIndex in 0..<batchCount) {
            val (g, batchLoss) = Tuner.gradient(batches[batchIndex], parameters)

            trainingLoss += batchLoss / batchCount

            for (i in 0..<gradientAcc.size) {
                gradientAcc[i] += g[i] * lr

                parameters[i] += gradientAcc[i].toInt()
                gradientAcc[i] -= gradientAcc[i].toInt()
            }
        }

        if (epoch % 10 == 0) {
            if (epoch % 100 == 0) {
                printPst(parameters)
                println("passed: ${parameters[Eval.PASSED_INDEX]}")
                println("bishop pair: ${parameters[Eval.BISHOP_PAIR_INDEX]}")
            }

            val testingLoss = Tuner.loss(testBatch, parameters)
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
    println("passed: ${parameters[Eval.PASSED_INDEX]}")
    println("bishop pair: ${parameters[Eval.BISHOP_PAIR_INDEX]}")

    printParameters(parameters, 0, parameters.size)

    val testingLoss = Tuner.loss(batches[0], parameters)
    println("final results: testing loss: $testingLoss")

}

fun printPst(parameters: IntArray) {
    for (piece in 0..5) {
        val pieceName = PieceType(piece).name

        println("midgame king quad $pieceName table")
        printSinglePst(parameters, Eval.PST_INDEX + piece * 2 * 2 * 64)
        println()

        println("endgame king quad $pieceName table")
        printSinglePst(parameters, Eval.PST_INDEX + piece * 2 * 2 * 64 + 64)
        println()

        println("midgame non king quad $pieceName table")
        printSinglePst(parameters, Eval.PST_INDEX + piece * 2 * 2 * 64 + 2 * 64)
        println()

        println("endgame non king quad $pieceName table")
        printSinglePst(parameters, Eval.PST_INDEX + piece * 2 * 2 * 64 + 2 * 64 + 64)
        println()
    }
}

fun printSinglePst(parameters: IntArray, index: Int) {
    for (rank in 7 downTo 0) {
        for (file in 0..7) {
            print("%6d".format(parameters[index + Square(rank, file).value]))
        }
        println()
    }
}

fun printParameters(parameters: IntArray, startIndex: Int, amount: Int) {
    for (i in startIndex..<startIndex+amount) {
        print("${parameters[i]}, ")
    }
    println()
}
