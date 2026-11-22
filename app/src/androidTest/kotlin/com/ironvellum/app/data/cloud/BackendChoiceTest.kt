package com.ironvellum.app.data.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.platform.app.InstrumentationRegistry
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import io.ktor.client.engine.mock.MockEngine
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BackendChoiceTest {
    @Test fun failedCommitRestoresDefaultAndCustomChoicesBeforeAnotherWrite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fallback = CloudConfig("", "", true)
        val next = CloudConfig("https://new.invalid", "public-new", false)
        for (original in listOf(null, CloudConfig("https://prior.invalid", "prior", false),
                CloudConfig("https://prior.invalid", "prior", false, "old-scope"))) {
            val name = "backend-commit-" + UUID.randomUUID()
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            try {
                BackendChoice(prefs, fallback).persist(original, original)
                val reject = object : SharedPreferences by prefs {
                    var failNext = true
                    override fun edit(): SharedPreferences.Editor {
                        val editor = prefs.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }
                            override fun commit(): Boolean {
                                val written = editor.commit()
                                if (failNext) {
                                    failNext = false
                                    assertTrue(written)
                                    assertEquals(next.url, prefs.getString("url", null))
                                    return false
                                }
                                return written
                            }
                        }
                    }
                }
                val choices = BackendChoice(reject, fallback)
                val error = runCatching { choices.persist(next, choices.fresh(next)) }.exceptionOrNull()
                assertTrue(error is java.io.IOException)
                assertTrue(prefs.edit().putString("tombstone_owner", "unrelated").commit())
                assertEquals(original, BackendChoice(prefs, fallback).read())
                val expected = buildMap {
                    put("tombstone_owner", "unrelated")
                    original?.let {
                        put("url", it.url)
                        put("key", it.key)
                        it.authNamespace?.let { ns -> put("auth_namespace", ns) }
                    }
                }
                // Read the committed XML independently of Android's process-wide preference cache.
                val file = java.io.File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
                val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val strings = xml.getElementsByTagName("string")
                val onDisk = (0 until strings.length).associate { i ->
                    val entry = strings.item(i) as org.w3c.dom.Element
                    entry.getAttribute("name") to entry.textContent
                }
                assertEquals(expected, onDisk)
            } finally {
                context.deleteSharedPreferences(name)
            }
        }
    }

    @Test fun explicitChoiceAfterOfflineStartupGetsFreshStorageAndKeepsItAcrossRestart() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "backend-choice-" + UUID.randomUUID()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val settings = SharedPreferencesSettings(prefs)
        val shared = CloudConfig("https://retirement.one.invalid", "public", true)
        val custom = CloudConfig("https://retirement-one.invalid", "custom", false)
        val keyFn = Class.forName("io.github.jan.supabase.auth.SettingsUtilKt")
            .getMethod("createDefaultSettingsKey", String::class.java)
        fun legacyKey(cfg: CloudConfig) = (keyFn.invoke(null, cfg.url.substringAfter("//")) as String) + "-session"
        assertEquals(legacyKey(shared), legacyKey(custom))
        val legacy = SettingsSessionManager(settings, legacyKey(shared))
        val fixture = UserSession(accessToken = "local-test", refreshToken = "local-test",
            expiresIn = 3600, tokenType = "bearer")
        try {
            legacy.saveSession(fixture)
            assertEquals(shared, BackendChoice(prefs, shared).read())
            val blank = CloudConfig("", "", true)
            val choices = BackendChoice(prefs, blank)
            assertNull(choices.read())
            val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
            val builds = AtomicInteger()
            val requests = AtomicInteger()
            val selection = CloudClientSelection { cfg ->
                builds.incrementAndGet()
                val stores = checkNotNull(cfg.authStores(settings))
                buildCloudClient(cfg, owner.lifecycle, MockEngine {
                    requests.incrementAndGet()
                    error("Storage selection must be local")
                }, stores.first, stores.second)
            }
            selection.initialize(choices.read())
            assertTrue(selection.requireConfigured.isFailure)
            assertEquals(0, builds.get())
            val next = choices.fresh(custom)!!
            val manager = next.authStores(settings)!!.first
            assertNull(manager.loadSessionOrNull())
            assertTrue(runCatching { selection.select(next) { throw java.io.IOException("synthetic refusal") } }.isFailure)
            assertNull(selection.config.value)
            assertNull(choices.read())
            selection.select(next) { choices.persist(custom, next) }
            val client = selection.requireConfigured.getOrThrow()
            try {
                client.auth.awaitInitialization()
                assertNull(client.auth.currentSessionOrNull())
                assertEquals(1, builds.get())
                assertEquals(0, requests.get())
            } finally {
                client.close()
            }
            val restored = BackendChoice(prefs, blank).read()!!
            assertEquals(next, restored)
            manager.saveSession(fixture.copy(refreshToken = "new-local-test"))
            assertEquals("new-local-test", restored.authStores(settings)!!.first.loadSession().refreshToken)
            assertEquals("local-test", legacy.loadSession().refreshToken)
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
