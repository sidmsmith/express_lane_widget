package com.expresslanes.widget

data class FetchResult(
    val status: ExpressLaneStatus,
    val isOddResponse: Boolean,
    val rawJson: String,
    val lastUpdatedSeconds: Long?,
    /** True when status comes from Peach Pass (511 GA failed, stale, or odd). Widget uses yellow arrows. */
    val fromPeachPassFallback: Boolean = false
)
