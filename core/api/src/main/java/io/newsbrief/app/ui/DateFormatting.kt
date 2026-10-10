package io.newsbrief.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.forLanguageTag("ru"))
fun formatDate(millis: Long): String = dateFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
