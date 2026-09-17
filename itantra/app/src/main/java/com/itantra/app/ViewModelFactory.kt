package com.itantra.app

import androidx.compose.runtime.Composable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Constructs a [ViewModel] with manually-supplied dependencies (from
 * [AppGraph]) via a plain [ViewModelProvider.Factory], instead of a DI
 * framework. Uses only the long-stable `viewModel(factory = ...)`
 * Compose API, deliberately avoiding newer creation-lambda/DSL Compose
 * APIs whose exact availability varies by androidx.lifecycle patch
 * version — correctness here matters more than brevity.
 */
@Composable
inline fun <reified VM : ViewModel> viewModelWithFactory(crossinline creator: () -> VM): VM {
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
    }
    return viewModel(factory = factory)
}
