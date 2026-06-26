package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.UserDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.ISPViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ISPViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false) { // Set Professional Polish light theme as default
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                
                val authState by viewModel.authState.collectAsState()
                val selectedRole by viewModel.selectedRole.collectAsState()
                val toastMessage by viewModel.toastMessage.collectAsState()

                // State Router
                var currentScreen by remember { mutableStateOf("role_selection") }

                // Observe SnackBar notifications from Viewmodel
                LaunchedEffect(toastMessage) {
                    toastMessage?.let {
                        scope.launch {
                            snackbarHostState.showSnackbar(it)
                            viewModel.clearToast()
                        }
                    }
                }

                // Transition screen based on auth success
                LaunchedEffect(authState) {
                    when (val state = authState) {
                        is AuthState.Success -> {
                            currentScreen = if (state.user.role == "admin") {
                                "admin_dashboard"
                            } else {
                                "user_dashboard"
                            }
                        }
                        else -> {
                            // Keep current screen
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.testTag("app_snackbar")) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            "role_selection" -> {
                                RoleSelectionScreen(
                                    onRoleSelected = { role ->
                                        viewModel.selectRole(role)
                                        currentScreen = "login"
                                    }
                                )
                            }
                            "login" -> {
                                LoginScreen(
                                    viewModel = viewModel,
                                    role = selectedRole ?: "user",
                                    onBack = {
                                        viewModel.resetRole()
                                        currentScreen = "role_selection"
                                    }
                                )
                            }
                            "user_dashboard" -> {
                                UserDashboardScreen(
                                    viewModel = viewModel,
                                    onSignOut = {
                                        viewModel.resetRole()
                                        currentScreen = "role_selection"
                                    }
                                )
                            }
                            "admin_dashboard" -> {
                                AdminDashboardScreen(
                                    viewModel = viewModel,
                                    onSignOut = {
                                        viewModel.resetRole()
                                        currentScreen = "role_selection"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
