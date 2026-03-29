package com.expresslanes.widget

enum class ExpressLaneStatus {
    NORTHBOUND,
    SOUTHBOUND,
    CLOSED,
    /** Peach Pass: lanes in transition; direction unknown, not treated as closed. */
    TRANSITION,
    UNKNOWN
}
