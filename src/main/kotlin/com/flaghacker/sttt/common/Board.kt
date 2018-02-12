package com.flaghacker.sttt.common

import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.util.*

typealias Coord = Byte

fun toJSON(board: Board): JSONObject {
    val json = JSONObject()
    val jsonRows = JSONArray()
    for (row in board.getRows()) jsonRows.put(row)

    json.put("rows", jsonRows)
    json.put("macroMask", board.getMacroMask())
    json.put("nextPlayer", board.nextPlayer().niceString)
    json.put("lastMove", board.lastMove()?.toInt())
    json.put("wonBy", board.wonBy().niceString)

    return json
}

fun fromJSON(json: JSONObject): Board {
    val jsonRows = json.getJSONArray("rows")
    val macroMask = json.getInt("macroMask")
    val nextPlayer = fromNiceString(json.getString("nextPlayer"))
    val lastMove = json.getInt("lastMove").toByte()
    val wonBy = fromNiceString(json.getString("wonBy"))

    if (jsonRows.length() != 6 || macroMask < 0 || macroMask > 0b111111111 || lastMove > 80)
        throw IllegalArgumentException("json parsing failed (arguments: [$jsonRows,$macroMask,$lastMove])")

    return Board(Array(6) { jsonRows.get(it) as Int }, macroMask, nextPlayer, lastMove, wonBy)
}

class Board(
        /*
        Each element represents a row of macros (3 rows of 9 tiles)
        The first 3 Ints hold the macros for Player
        The next 3 Ints hold the macros for Enemy

        In each Int the bit representation is as follows:
        aaaaaaaaabbbbbbbbbcccccccccABC with:
        a/b/c: bit enabled if the player is the owner of the tile
        A/B/C: bit enabled if the player won the macro
        */
        private var rows: Array<Int>,
        private var macroMask: Int,
        private var nextPlayer: Player,
        private var lastMove: Coord?,
        private var wonBy: Player
) : Serializable {
    constructor() : this(Array(6) { 0 }, 0b111111111, Player.PLAYER, null, Player.NEUTRAL)
    constructor(rows: Array<Int>, macroMask: Int) : this(rows, macroMask, Player.PLAYER, null, Player.NEUTRAL)

    fun getRows() = rows.copyOf()
    fun getMacroMask() = macroMask
    fun nextPlayer() = nextPlayer
    fun lastMove() = lastMove
    fun wonBy() = wonBy

    fun isDone() = wonBy != Player.NEUTRAL || availableMoves().isEmpty()
    private fun getVal(mRow: Int, mNum: Int): Int = (rows[mRow] shr mNum) and 1
    private fun Int.getBit(index: Int) = ((this shr index) and 1) == 1
    private fun Int.isMaskSet(mask: Int) = this and mask == mask

    fun flip(): Board {
        val board = copy()

        val newRows = Array(6) { 0 }
        for (i in 0..2) newRows[i] = board.rows[i + 3]
        for (i in 3..5) newRows[i] = board.rows[i - 3]
        board.rows = newRows
        board.wonBy = board.wonBy.otherWithNeutral()
        board.nextPlayer = board.nextPlayer.otherWithNeutral()

        return board
    }

    fun copy(): Board {
        val copy = Board()
        copy.rows = rows.copyOf()
        copy.wonBy = wonBy
        copy.nextPlayer = nextPlayer
        copy.macroMask = macroMask
        copy.lastMove = lastMove
        return copy
    }

    fun play(x: Int, y: Int) = play((((x / 3) + (y / 3)*3)*9 + ((x % 3) + (y % 3) * 3)).toByte())
    fun play(index: Coord): Boolean {
        val row = index / 27 //Row 0,1,2
        val macroShift = (index / 9) % 3 * 9     //Shift to go to the right micro
        val moveShift = index % 9                //Shift required for index within matrix
        val shift = moveShift + macroShift

        //If the move is not available throw exception
        if ((rows[row] or rows[row + 3]).getBit(shift) || !macroMask.getBit((index / 27) * 3 + (macroShift / 9)))
            throw RuntimeException("Position $index not available")
        else if (wonBy != Player.NEUTRAL)
            throw RuntimeException("The game is over")

        //Write the move to the board
        val pRow = (nextPlayer.value - 1) * 3 + row
        rows[pRow] += (1 shl shift)

        val macroWin = wonGrid((rows[pRow] shr macroShift) and 0b111111111, moveShift)
        var winGrid: Int
        if (macroWin) {
            rows[pRow] += (1 shl (27 + macroShift / 9)) //27 + macro number

            //Create the winGrid of the player
            winGrid = (rows[0 + 3 * (nextPlayer.value - 1)] shr 27)
                    .or((rows[1 + 3 * (nextPlayer.value - 1)] shr 27) shl 3)
                    .or((rows[2 + 3 * (nextPlayer.value - 1)] shr 27) shl 6)

            if (wonGrid(winGrid, index / 9))
                wonBy = nextPlayer

            //Add the winGrid of the enemy
            winGrid = winGrid or (rows[0 + 3 * (nextPlayer.other().value - 1)] shr 27)
                    .or((rows[1 + 3 * (nextPlayer.other().value - 1)] shr 27) shl 3)
                    .or((rows[2 + 3 * (nextPlayer.other().value - 1)] shr 27) shl 6)
        } else {
            winGrid = ((rows[0] or rows[3]) shr 27)
                    .or(((rows[1] or rows[4]) shr 27) shl 3)
                    .or(((rows[2] or rows[5]) shr 27) shl 6)
        }

        //Prepare the board for the next player
        val freeMove = winGrid.getBit(moveShift) || macroFull(moveShift)
        macroMask =
                if (freeMove) (0b111111111 and winGrid.inv())
                else (1 shl moveShift)
        lastMove = index
        nextPlayer = nextPlayer.other()

        return macroWin
    }

    private fun macroFull(om: Int): Boolean = (rows[om / 3] or rows[3 + om / 3]).shr((om % 3) * 9).isMaskSet(0b111111111)

    fun availableMoves(): List<Coord> {
        val output = ArrayList<Coord>()

        for (macro in 0..8) {
            if (macroMask.getBit(macro)) {
                val row = rows[macro / 3] or rows[macro / 3 + 3]
                (0..8).map { it + macro * 9 }.filter { !row.getBit(it % 27) }.mapTo(output) { it.toByte() }
            }
        }

        return output
    }

    fun macro(om: Int): Player = when {
        rows[om / 3].getBit(27 + om % 3) -> Player.PLAYER
        rows[3 + om / 3].getBit(27 + om % 3) -> Player.ENEMY
        else -> Player.NEUTRAL
    }

    fun tile(o: Int): Player = when {
        rows[o / 27].getBit(o % 27) -> Player.PLAYER
        rows[3 + o / 27].getBit(o % 27) -> Player.ENEMY
        else -> Player.NEUTRAL
    }

    private fun wonGrid(grid: Int, index: Int): Boolean {
        when (index) {
            4 ->                                                        //Center
                return grid.getBit(1) && grid.getBit(7)                 //line |
                        || grid.getBit(3) && grid.getBit(5)             //line -
                        || grid.getBit(0) && grid.getBit(8)             //line \
                        || grid.getBit(6) && grid.getBit(2)             //line /
            3, 5 ->                                                     //Horizontal sides
                return grid.getBit(index - 3) && grid.getBit(index + 3) //line |
                        || grid.getBit(4) && grid.getBit(8 - index)     //line -
            1, 7 ->                                                     //Vertical sides
                return grid.getBit(index - 1) && grid.getBit(index + 1) //line |
                        || grid.getBit(4) && grid.getBit(8 - index)     //line -
            else -> {   //corners
                val x = index % 3
                val y = index / 3
                return grid.getBit(4) && grid.getBit(8 - index)                                    //line \ or /
                        || grid.getBit(3 * y + (x + 1) % 2) && grid.getBit(3 * y + (x + 2) % 4)    //line -
                        || grid.getBit(x + ((y + 1) % 2) * 3) && grid.getBit(x + ((y + 2) % 4) * 3)//line |
            }
        }
    }

    override fun toString(): String {
        var output = ""
        for (i in 0..80) {
            val tileShift = i % 3 + 3 * ((i / 9) % 3)
            val macroShift = (i % 9) / 3 * 9
            val shift = tileShift + macroShift

            output +=
                    if (i == 0 || i == 80) ""
                    else if (i % 27 == 0) "\n---+---+---\n"
                    else if (i % 9 == 0) "\n"
                    else if (i % 3 == 0 || i % 6 == 0) "|"
                    else ""

            output += when {
                getVal(i / 27, shift) == 1 -> "X"
                getVal(i / 27 + 3, shift) == 1 -> "O"
                else -> " "
            }
        }
        return output
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Board

        if (!Arrays.equals(rows, other.rows)) return false
        if (lastMove != other.lastMove) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(rows)
        result = 31 * result + (lastMove ?: 0)
        return result
    }
}