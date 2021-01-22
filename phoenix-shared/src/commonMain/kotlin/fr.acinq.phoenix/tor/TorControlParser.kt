package fr.acinq.phoenix.tor

import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class TorControlParser(loggerFactory: LoggerFactory) {

    private val logger = newLogger(loggerFactory)

    sealed class ParseState {
        abstract fun parseLine(logger: Logger, line: String): Pair<ParseState, TorControlResponse?>

        protected fun parseReplyLine(line: String): Triple<Int, Char, String> {
            val trimmedLine = line.trim()
            val status = trimmedLine.substring(0, 3).toInt()
            val type = trimmedLine[3]
            val reply = trimmedLine.substring(4)
            return Triple(status, type, reply)
        }

        protected fun replyLineNextState(status: Int, previousReplies: List<TorControlResponse.Reply>, type: Char, currentReply: String): Pair<ParseState, TorControlResponse?> =
            when (type) {
                '-' -> BuildingReply(status, previousReplies + TorControlResponse.Reply(currentReply, null)) to null
                '+' -> AwaitingReplyData(status, previousReplies, currentReply, "") to null
                ' ' -> AwaitingReply to TorControlResponse(status, previousReplies + TorControlResponse.Reply(currentReply, null))
                else -> error("Unknown line type with separator '$type'")
            }

        object AwaitingReply : ParseState() {
            override fun parseLine(logger: Logger, line: String): Pair<ParseState, TorControlResponse?> {
                val (status, type, reply) = parseReplyLine(line)
                return replyLineNextState(status, emptyList(), type, reply)
            }
        }

        data class BuildingReply(val status: Int, val replies: List<TorControlResponse.Reply>) : ParseState() {
            override fun parseLine(logger: Logger, line: String): Pair<ParseState, TorControlResponse?> {
                val (status, type, reply) = parseReplyLine(line)
                if (status != this.status) logger.warning { "Got additional reply line with different status (line: $status, original: ${this.status})" }
                return replyLineNextState(this.status, this.replies, type, reply)
            }
        }

        data class AwaitingReplyData(val status: Int, val previousReplies: List<TorControlResponse.Reply>, val currentReply: String, val currentData: String) : ParseState() {
            override fun parseLine(logger: Logger, line: String): Pair<ParseState, TorControlResponse?> {
                if (line.trim() == ".") {
                    return BuildingReply(status, previousReplies + TorControlResponse.Reply(currentReply, currentData)) to null
                }
                return AwaitingReplyData(status, previousReplies, currentReply, currentData + line) to null
            }
        }
    }

    var state: ParseState = ParseState.AwaitingReply

    fun parseLine(line: String): TorControlResponse? {
        val (nextState, response) = state.parseLine(logger, line)
        state = nextState
        return response
    }

    fun reset() {
        state = ParseState.AwaitingReply
    }
}
