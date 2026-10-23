package party.elias.erinn.nnue

class AccumulatorDiff {

    var addFeatures: IntArray = IntArray(2)
        private set
    var addFeatureCount: Int = 0
        private set
    var subFeatures: IntArray = IntArray(2)
        private set
    var subFeatureCount: Int = 0
        private set

    fun addFeature(feature: Int) {
        addFeatures[addFeatureCount++] = feature
    }

    fun subFeature(feature: Int) {
        subFeatures[subFeatureCount++] = feature
    }

    fun reset() {
        addFeatureCount = 0
        subFeatureCount = 0
    }
}