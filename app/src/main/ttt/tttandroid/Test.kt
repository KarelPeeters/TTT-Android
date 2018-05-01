package ttt.tttandroid

fun main(args: Array<String>) {

    val x = -5

    println(Integer.toBinaryString(x and 0xff))
    println(Integer.toBinaryString((x shr 8) and 0xff))



}