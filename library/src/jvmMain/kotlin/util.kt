fun <K, V> Map<K, V>.withIndex(): List<Triple<Int, K, V>> =
    entries.withIndex().map { (index, entry) -> Triple(index, entry.key, entry.value) }

fun <A, B> Collection<Pair<A, B>>.withIndex(): List<Triple<Int, A, B>> =
    mapIndexed { index, pair -> Triple(index, pair.first, pair.second) }
