package com.example.app.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.compounds.domain.CreateReusableCompoundUseCase

class CreateCompoundViewModelFactory(
    private val createReusableCompoundUseCase: CreateReusableCompoundUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateCompoundViewModel::class.java)) {
            return CreateCompoundViewModel(createReusableCompoundUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
