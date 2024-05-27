fun <K, V> Map<K, V>.withIndex(): List<Triple<Int, K, V>> =
    entries.withIndex().map { Triple(it.index, it.value.key, it.value.value) }
