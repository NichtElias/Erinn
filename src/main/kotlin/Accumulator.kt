package party.elias

abstract class Accumulator {
    val contents: IntArray = IntArray(NNUE.ACC_HALF_WITH_PSQT_SIZE)
    val diff: AccumulatorDiff = AccumulatorDiff()
    abstract val color: Color
    var dirty: Boolean = true

    // only set when full refresh is required
    var fullRefreshData: FullRefreshData? = null

    fun fullRefresh() {

        for (i in 0..<NNUE.ACC_HALF_WITH_PSQT_SIZE) {
            contents[i] = NNUE.ftBiases[i]
        }

        fullRefreshData!!.forEachFeature { piece, square ->
            val weights = NNUE.ftWeights[feature(piece, square, fullRefreshData!!.kingSquare)]

            for (i in 0..<NNUE.ACC_HALF_WITH_PSQT_SIZE) {
                contents[i] += weights[i]
            }
        }

        diff.reset()
        dirty = false
        fullRefreshData = null
    }

    fun applyDiff(parentAcc: Accumulator) {
        when (diff.addFeatureCount) {
            0 -> if (diff.subFeatureCount == 0) copy(parentAcc)
            1 -> when (diff.subFeatureCount) {
                1 -> addSubDiff(parentAcc)
                2 -> addSubSubDiff(parentAcc)
            }
            2 -> if (diff.subFeatureCount == 2) addAddSubSubDiff(parentAcc)
        }

        diff.reset()
        dirty = false
    }

    fun addSubDiff(from: Accumulator) {
        val fromContents = from.contents

        val addFeatureWeights = NNUE.ftWeights[diff.addFeatures[0]]
        val subFeatureWeights = NNUE.ftWeights[diff.subFeatures[0]]

        for (i in 0..<NNUE.ACC_HALF_WITH_PSQT_SIZE) {
            contents[i] = fromContents[i] + addFeatureWeights[i] - subFeatureWeights[i]
        }
    }

    fun addSubSubDiff(from: Accumulator) {
        val fromContents = from.contents

        val addFeatureWeights = NNUE.ftWeights[diff.addFeatures[0]]
        val sub0FeatureWeights = NNUE.ftWeights[diff.subFeatures[0]]
        val sub1FeatureWeights = NNUE.ftWeights[diff.subFeatures[1]]

        for (i in 0..<NNUE.ACC_HALF_WITH_PSQT_SIZE) {
            contents[i] = fromContents[i] + addFeatureWeights[i] - sub0FeatureWeights[i] - sub1FeatureWeights[i]
        }
    }

    fun addAddSubSubDiff(from: Accumulator) {
        val fromContents = from.contents

        val add0FeatureWeights = NNUE.ftWeights[diff.addFeatures[0]]
        val add1FeatureWeights = NNUE.ftWeights[diff.addFeatures[1]]
        val sub0FeatureWeights = NNUE.ftWeights[diff.subFeatures[0]]
        val sub1FeatureWeights = NNUE.ftWeights[diff.subFeatures[1]]

        for (i in 0..<NNUE.ACC_HALF_WITH_PSQT_SIZE) {
            contents[i] = fromContents[i] + add0FeatureWeights[i] + add1FeatureWeights[i] - sub0FeatureWeights[i] - sub1FeatureWeights[i]
        }
    }

    // required for null move
    fun copy(from: Accumulator) {
        from.contents.copyInto(contents)
    }

    abstract fun feature(piece: Piece, square: Square, kingSquare: Square): Int

    class FullRefreshData(val pieces: PieceArray, val kingSquare: Square) {

        fun forEachFeature(func: (piece: Piece, square: Square) -> Unit) {

            for ((sqi, rawPiece) in pieces.array.withIndex()) {
                val piece = Piece(rawPiece)
                if (piece != Piece.NONE) {
                    func(piece, Square(sqi))
                }
            }

        }

        companion object {
            fun fromBoard(board: Board, accumulatorColor: Color): FullRefreshData {
                return FullRefreshData(PieceArray(board.pieces.array.clone()), board.kingSquares[accumulatorColor.idx()])
            }
        }
    }

    class White : Accumulator() {
        override val color: Color = Color.WHITE

        override fun feature(piece: Piece, square: Square, kingSquare: Square): Int {
            return NNUE.whiteFeature(piece, square, kingSquare)
        }
    }

    class Black : Accumulator() {
        override val color: Color = Color.BLACK

        override fun feature(piece: Piece, square: Square, kingSquare: Square): Int {
            return NNUE.blackFeature(piece, square, kingSquare)
        }
    }
}