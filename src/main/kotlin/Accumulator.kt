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
        val parentContents = parentAcc.contents

        parentContents.copyInto(contents)

        for (i in 0..<diff.subFeatureCount) {
            val featureWeights = NNUE.ftWeights[diff.subFeatures[i]]

            for (j in 0..<NNUE.ACC_HALF_WITH_PSQT_SIZE) {
                contents[j] -= featureWeights[j]
            }
        }

        for (i in 0..<diff.addFeatureCount) {
            val featureWeights = NNUE.ftWeights[diff.addFeatures[i]]

            for (j in 0..<NNUE.ACC_HALF_WITH_PSQT_SIZE) {
                contents[j] += featureWeights[j]
            }
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