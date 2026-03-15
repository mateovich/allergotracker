package com.example.app.navigation

object AppRoutes {
    const val RECORD_ID_ARG = "recordId"
    const val RECORDS = "records"
    const val CREATE_INGESTION = "create/ingestion"
    const val CREATE_EXPOSURE = "create/exposure"
    const val CREATE_OUTBREAK = "create/outbreak"
    const val EDIT_INGESTION = "edit/ingestion/{$RECORD_ID_ARG}"
    const val EDIT_EXPOSURE = "edit/exposure/{$RECORD_ID_ARG}"
    const val EDIT_OUTBREAK = "edit/outbreak/{$RECORD_ID_ARG}"

    fun editIngestion(recordId: Long): String = "edit/ingestion/$recordId"

    fun editExposure(recordId: Long): String = "edit/exposure/$recordId"

    fun editOutbreak(recordId: Long): String = "edit/outbreak/$recordId"
}
