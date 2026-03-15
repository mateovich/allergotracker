package com.example.app.features.home.data

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.core.database.HomeDao
import com.example.app.features.home.domain.HomeRepository

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
