package com.example.jetsoncontroller.ui.storage

import android.content.Context
import com.example.jetsoncontroller.data.server.ServerEndpointProfile
import com.example.jetsoncontroller.data.server.ServerEnvironment
import com.example.jetsoncontroller.data.server.ServerProfileStore
import com.example.jetsoncontroller.data.server.StoredServerProfile
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

@OptIn(ExperimentalCoroutinesApi::class)
class DirectServerProfileSaveTest {
    @Test
    fun `add and edit complete without leaving save or connect busy`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val profiles = MutableStateFlow<List<StoredServerProfile>>(emptyList())
            var failSave = false
            Mockito.mockConstruction(ServerProfileStore::class.java,
                Mockito.withSettings().defaultAnswer { invocation ->
                    when (invocation.method.name) {
                        "getProfiles" -> profiles
                        "save" -> {
                            if (failSave) throw IllegalArgumentException("invalid profile")
                            val profile = invocation.getArgument<ServerEndpointProfile>(0)
                            profiles.value = listOf(StoredServerProfile(profile, "revision"))
                            Unit
                        }
                        else -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
                    }
                }).use {
                val context = Mockito.mock(Context::class.java)
                Mockito.`when`(context.noBackupFilesDir).thenReturn(File("unused-profile-test"))
                val vm = DirectServerViewModel(context)
                testScheduler.runCurrent()
                val profile = ServerEndpointProfile("expo", "전시 서버", ServerEnvironment.DEVELOPMENT,
                    "https://example.com", "employee", "project")
                vm.saveProfile(profile, "emp_example")
                testScheduler.runCurrent()
                assertEquals(profile, vm.uiState.value.profiles.single().profile)
                assertEquals("expo", vm.uiState.value.selectedProfileId)
                assertFalse(vm.uiState.value.messageIsError)
                assertNotNull(vm.uiState.value.message)
                assertIdle(vm.uiState.value)

                val edited = profile.copy(displayName = "수정 서버")
                vm.saveProfile(edited, "emp_updated")
                testScheduler.runCurrent()
                assertEquals(edited, vm.uiState.value.profiles.single().profile)
                assertIdle(vm.uiState.value)

                failSave = true
                vm.saveProfile(edited, "emp_invalid")
                testScheduler.runCurrent()
                assertTrue(vm.uiState.value.messageIsError)
                assertIdle(vm.uiState.value)
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun `cancelled save clears spinner and stale completion cannot finish a newer save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = mutableListOf<CompletableDeferred<Unit>>()
            val profiles = MutableStateFlow<List<StoredServerProfile>>(emptyList())
            Mockito.mockConstruction(ServerProfileStore::class.java,
                Mockito.withSettings().defaultAnswer { invocation ->
                    if (invocation.method.name == "getProfiles") return@defaultAnswer profiles
                    if (invocation.method.name != "save") return@defaultAnswer org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
                // The suspend boundary is provided by a real deferred, with the
                // continuation forwarded exactly as a datastore write would suspend.
                    val gate = CompletableDeferred<Unit>().also { pending += it }
                    val wait: suspend () -> Unit = { gate.await() }
                    @Suppress("UNCHECKED_CAST")
                    wait.startCoroutineUninterceptedOrReturn(invocation.rawArguments.last() as kotlin.coroutines.Continuation<Unit>)
                }).use {
                val context = Mockito.mock(Context::class.java)
                Mockito.`when`(context.noBackupFilesDir).thenReturn(File("unused-profile-test"))
                val vm = DirectServerViewModel(context)
                val profile = ServerEndpointProfile("expo", "전시 서버", ServerEnvironment.DEVELOPMENT,
                    "https://example.com", "employee", "project")
                vm.saveProfile(profile, "emp_first")
                testScheduler.runCurrent()
                assertTrue(vm.uiState.value.isSavingProfile)
                assertFalse(vm.uiState.value.isLoading)
                vm.saveProfile(profile, "emp_second")
                testScheduler.runCurrent()
                pending.first().complete(Unit)
                testScheduler.runCurrent()
                assertTrue(vm.uiState.value.isSavingProfile)
                pending.last().complete(Unit)
                testScheduler.runCurrent()
                assertIdle(vm.uiState.value)
                vm.saveProfile(profile, "emp_third")
                testScheduler.runCurrent()
                vm.selectSection(DirectServerSection.PROFILES)
                testScheduler.runCurrent()
                assertIdle(vm.uiState.value)
            }
        } finally { Dispatchers.resetMain() }
    }

    private fun assertIdle(state: DirectServerUiState) {
        assertFalse(state.isSavingProfile)
        assertFalse(state.isLoading)
        assertFalse(state.isConnecting)
    }
}
