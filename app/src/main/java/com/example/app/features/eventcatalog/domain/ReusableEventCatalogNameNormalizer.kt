package com.example.app.features.eventcatalog.domain

private val internalWhitespaceRegex = Regex("\\s+")

fun canonicalizeReusableEventCatalogName(name: String): String {
    return internalWhitespaceRegex.replace(name.trim(), " ")
}

fun normalizeReusableEventCatalogName(name: String): String {
    return canonicalizeReusableEventCatalogName(name).lowercase()
}
