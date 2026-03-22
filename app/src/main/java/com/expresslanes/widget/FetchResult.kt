package com.expresslanes.widget

data class FetchResult(
    val status: ExpressLaneStatus,
    val isOddResponse: Boolean,
    val rawJson: String,
    val lastUpdatedSeconds: Long?
)
