package com.monarch.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.monarch.app.MonarchApp
import com.monarch.app.data.HealthSync
import com.monarch.app.data.Repository

/** Access the app-scoped repository from any ViewModel factory. */
fun CreationExtras.monarchRepository(): Repository =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MonarchApp).repository

fun CreationExtras.monarchHealthSync(): HealthSync =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MonarchApp).healthSync
