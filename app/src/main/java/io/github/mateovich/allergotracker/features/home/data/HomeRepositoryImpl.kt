package io.github.mateovich.allergotracker.features.home.data

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.database.HomeDao
import io.github.mateovich.allergotracker.features.home.domain.HomeRepository

class HomeRepositoryImpl(
    private val homeDao: HomeDao
) : HomeRepository {
    override fun loadWelcomeMessage(): AppResult<String> {
        return try {
            AppResult.Success(homeDao.getWelcomeMessage())
        } catch (exception: Exception) {
            AppResult.Error(DomainError.PersistenceError("Local persistence operation failed"))
        }
    }
}
