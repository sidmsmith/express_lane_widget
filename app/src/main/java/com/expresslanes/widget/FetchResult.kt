package com.expresslanes.widget

data class FetchResult(
    val status: ExpressLaneStatus,
    val isOddResponse: Boolean,
    val rawJson: String,
    val lastUpdatedSeconds: Long?,
    /** True when status comes from Peach Pass (511 GA failed, stale, or odd). Widget uses yellow arrows. */
    val fromPeachPassFallback: Boolean = false,
    /**
     * True when the Peach Pass fallback body could not be parsed reliably (missing `data.north`, no open/closed
     * cues, invalid JSON, HTTP error, etc.). Drives its own notification toggle separate from 511 "odd" responses.
     */
    val isPeachPassUnexpected: Boolean = false
)
