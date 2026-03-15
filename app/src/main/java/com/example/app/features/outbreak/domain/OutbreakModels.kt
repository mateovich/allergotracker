package com.example.app.features.outbreak.domain

data class CreateOutbreakEventCommand(
    val outbreakDescriptor: String,
    val bodyLocation: String,
    val occurredAtEpochMs: Long,
    val notes: String?
)

data class UpdateOutbreakEventCommand(
    val id: Long,
    val outbreakDescriptor: String,
    val bodyLocation: String,
    val occurredAtEpochMs: Long,
    val notes: String?
)

data class OutbreakEvent(
    val id: Long,
    val outbreakDescriptor: String,
    val bodyLocation: String,
    val occurredAtEpochMs: Long,
    val notes: String?
)

data class CreateOutbreakDefaults(
    val occurredAtEpochMs: Long
)
