package com.example.app.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.compounds.domain.GetReusableCompoundUseCase
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase

class EditCompoundViewModelFactory(
    private val compoundId: Long,
    private val getReusableCompoundUseCase: GetReusableCompoundUseCase,
    private val updateReusableCompoundUseCase: UpdateReusableCompoundUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditCompoundViewModel::class.java)) {
            return EditCompoundViewModel(
                compoundId = compoundId,
                getReusableCompoundUseCase = getReusableCompoundUseCase,
                updateReusableCompoundUseCase = updateReusableCompoundUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
