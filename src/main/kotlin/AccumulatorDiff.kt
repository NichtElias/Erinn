package party.elias

class AccumulatorDiff {

    var addFeatures: IntArray = IntArray(2)
    var addFeatureCount: Int = 0
    var subFeatures: IntArray = IntArray(2)
    var subFeatureCount: Int = 0

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