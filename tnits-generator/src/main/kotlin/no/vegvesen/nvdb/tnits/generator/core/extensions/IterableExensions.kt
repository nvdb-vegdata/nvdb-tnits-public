package no.vegvesen.nvdb.tnits.generator.core.extensions

inline fun <reified T> Iterable<T>.forEachChunked(size: Int, block: (List<T>) -> Unit) {
    require(size > 0) { "size must be > 0" }
    val it = iterator()
    while (it.hasNext()) {
        val chunk = ArrayList<T>(size)
        var count = 0
        while (count < size && it.hasNext()) {
            chunk += it.next()
            count++
        }
        block(chunk)
    }
}
