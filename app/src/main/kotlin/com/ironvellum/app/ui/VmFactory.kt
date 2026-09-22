package com.ironvellum.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.HealthSync
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.CloudSync

/** Access the app-scoped repository from any ViewModel factory. */
fun CreationExtras.ironvellumRepository(): Repository =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronvellumApp).repository

fun CreationExtras.ironvellumHealthSync(): HealthSync =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronvellumApp).healthSync

/**
 * Cloud dependencies are app-scoped like the repository: the auth session must
 * survive screen changes, and two clients would mean two sessions.
 */
fun CreationExtras.ironvellumAccount(): AccountRepository =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronvellumApp).accountRepository

fun CreationExtras.ironvellumCloudSync(): CloudSync =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronvellumApp).cloudSync
