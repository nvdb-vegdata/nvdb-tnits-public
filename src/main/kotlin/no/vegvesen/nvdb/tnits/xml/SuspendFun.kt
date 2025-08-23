package no.vegvesen.nvdb.tnits.xml

suspend fun main() {
    suspendSecond()
}

fun blockingFun() {
    wrapper {
    }
}

suspend fun suspendFirst() {
    println("Hello world")
}

suspend fun suspendSecond() {
    wrapper {
        suspendFirst()
    }
}

inline fun wrapper(block: () -> Unit) {
    block()
}
