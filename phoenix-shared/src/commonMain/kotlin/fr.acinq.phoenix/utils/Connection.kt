package fr.acinq.phoenix.utils

import fr.acinq.eclair.utils.Connection

operator fun Connection?.plus(other: Connection?) : Connection =
    when {
        this == other && this != null -> this
        this == Connection.ESTABLISHING || other == Connection.ESTABLISHING -> Connection.ESTABLISHING
        this == Connection.CLOSED || other == Connection.CLOSED -> Connection.CLOSED
        else -> this ?: other ?: error("Cannot add [$this + $other]")
    }
