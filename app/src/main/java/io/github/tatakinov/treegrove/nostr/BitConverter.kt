package io.github.tatakinov.treegrove.nostr

object BitConverter {
    fun convert(data : ByteArray, from : Int, to : Int, padding : Boolean) : ByteArray {
        var result  = ByteArray(0)
        if (data.isEmpty()) {
            return result
        }
        var remainFrom = from
        var remainTo   = to
        var index   = 0
        var input : Int = data[0].toUByte().toInt()
        var output : Int    = 0x00
        while (true) {
            var nextIn : Boolean
            var nextOut : Boolean
            if (remainFrom == remainTo) {
                output  = output xor input
                nextIn = true
                nextOut    = true
                remainFrom = 0
                remainTo   = 0
            }
            else if (remainTo > remainFrom) {
                remainTo   -= remainFrom
                output  = output xor (input shl remainTo)
                nextIn = true
                nextOut    = false
            }
            else {
                remainFrom -= remainTo
                output  = output xor (input shr remainFrom)
                input   = input and ((0x01 shl remainFrom) - 1)
                nextIn =   false
                nextOut    = true
            }
            if (nextIn) {
                index++
                if (index >= data.size) {
                    break
                }
                input   = data[index].toUByte().toInt()
                remainFrom = from
            }
            if (nextOut) {
                result  += output.toByte()
                output  = 0x00
                remainTo   = to
            }
        }
        if (remainTo > 0 && padding) {
            result  += output.toByte()
        }
        return result
    }
}