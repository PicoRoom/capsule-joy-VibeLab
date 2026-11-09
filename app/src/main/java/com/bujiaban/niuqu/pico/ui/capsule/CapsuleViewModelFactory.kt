package com.bujiaban.niuqu.pico.ui.capsule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bujiaban.niuqu.pico.data.repository.LocalCapsuleRepository
import com.bujiaban.niuqu.pico.data.repository.LocalOnboardingPreferences
import com.bujiaban.niuqu.pico.domain.usecase.CapsuleUseCase

class CapsuleViewModelFactory(private val context:Context):ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T {
        require(modelClass==CapsuleViewModel::class.java)
        return CapsuleViewModel(CapsuleUseCase(LocalCapsuleRepository(context.applicationContext)),LocalOnboardingPreferences(context.applicationContext)) as T
    }
}
