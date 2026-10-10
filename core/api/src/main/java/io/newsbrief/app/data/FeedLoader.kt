package io.newsbrief.app.data

fun interface FeedLoader { suspend fun refresh(): RefreshResult }
