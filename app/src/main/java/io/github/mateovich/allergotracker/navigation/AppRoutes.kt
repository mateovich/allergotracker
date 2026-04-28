package io.github.mateovich.allergotracker.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AppRoutes {
    const val RECORD_ID_ARG = "recordId"
    const val REUSABLE_ID_ARG = "reusableId"
    const val RECORD_EVENT_TYPE_ARG = "eventType"
    const val CATALOG_KIND_ARG = "catalogKind"
    const val OPEN_DELETE_ARG = "openDelete"
    const val PREFILL_NAME_ARG = "prefillName"
    const val SEED_INGREDIENT_ID_ARG = "seedIngredientId"
    const val SEED_INGREDIENT_NAME_ARG = "seedIngredientName"
    const val RECORDS = "records"
    const val EXPORT = "export"
    const val RECORD_DETAIL = "record-detail/{$RECORD_EVENT_TYPE_ARG}/{$RECORD_ID_ARG}"
    const val CREATE_INGESTION = "create/ingestion"
    const val CREATE_EXPOSURE = "create/exposure"
    const val CREATE_OUTBREAK = "create/outbreak"
    const val CREATE_INGREDIENT = "create/ingredient"
    const val CREATE_COMPOUND = "create/compound?$PREFILL_NAME_ARG={$PREFILL_NAME_ARG}&$SEED_INGREDIENT_ID_ARG={$SEED_INGREDIENT_ID_ARG}&$SEED_INGREDIENT_NAME_ARG={$SEED_INGREDIENT_NAME_ARG}"
    const val EDIT_INGREDIENT = "edit/ingredient/{$REUSABLE_ID_ARG}"
    const val EDIT_COMPOUND = "edit/compound/{$REUSABLE_ID_ARG}"
    const val EDIT_EVENT_CATALOG = "edit/catalog/{$CATALOG_KIND_ARG}/{$REUSABLE_ID_ARG}"
    const val EDIT_INGESTION = "edit/ingestion/{$RECORD_ID_ARG}?$OPEN_DELETE_ARG={$OPEN_DELETE_ARG}"
    const val EDIT_EXPOSURE = "edit/exposure/{$RECORD_ID_ARG}?$OPEN_DELETE_ARG={$OPEN_DELETE_ARG}"
    const val EDIT_OUTBREAK = "edit/outbreak/{$RECORD_ID_ARG}?$OPEN_DELETE_ARG={$OPEN_DELETE_ARG}"
    const val REUSABLE_ENTITY_ID_RESULT = "reusableEntityId"
    const val REUSABLE_ENTITY_NAME_RESULT = "reusableEntityName"
    const val REUSABLE_ENTITY_KIND_RESULT = "reusableEntityKind"
    const val REUSABLE_ENTITY_RETIRED_RESULT = "reusableEntityRetired"
    const val EVENT_CATALOG_ID_RESULT = "eventCatalogId"
    const val EVENT_CATALOG_NAME_RESULT = "eventCatalogName"
    const val EVENT_CATALOG_KIND_RESULT = "eventCatalogKind"
    const val EVENT_CATALOG_RETIRED_KIND_RESULT = "eventCatalogRetiredKind"

    fun editIngredient(reusableId: Long): String = "edit/ingredient/$reusableId"

    fun createCompound(
        prefillName: String? = null,
        seedIngredientId: Long? = null,
        seedIngredientName: String? = null
    ): String {
        val encodedPrefillName = prefillName.orEmpty().urlEncode()
        val encodedSeedIngredientName = seedIngredientName.orEmpty().urlEncode()
        val seedId = seedIngredientId ?: -1L
        return "create/compound?$PREFILL_NAME_ARG=$encodedPrefillName&$SEED_INGREDIENT_ID_ARG=$seedId&$SEED_INGREDIENT_NAME_ARG=$encodedSeedIngredientName"
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    fun editCompound(reusableId: Long): String = "edit/compound/$reusableId"

    fun editEventCatalog(kind: String, reusableId: Long): String = "edit/catalog/$kind/$reusableId"

    fun editIngestion(recordId: Long, openDelete: Boolean = false): String {
        return if (openDelete) {
            "edit/ingestion/$recordId?$OPEN_DELETE_ARG=true"
        } else {
            "edit/ingestion/$recordId"
        }
    }

    fun editExposure(recordId: Long, openDelete: Boolean = false): String = "edit/exposure/$recordId?$OPEN_DELETE_ARG=$openDelete"

    fun editOutbreak(recordId: Long, openDelete: Boolean = false): String {
        return "edit/outbreak/$recordId?$OPEN_DELETE_ARG=$openDelete"
    }

    fun recordDetail(eventType: io.github.mateovich.allergotracker.features.records.domain.RecordEventType, recordId: Long): String {
        return "record-detail/${eventType.name.lowercase()}/$recordId"
    }

    fun parseRecordEventType(rawValue: String): io.github.mateovich.allergotracker.features.records.domain.RecordEventType? {
        return io.github.mateovich.allergotracker.features.records.domain.RecordEventType.entries.firstOrNull {
            it.name.equals(rawValue, ignoreCase = true)
        }
    }
}
