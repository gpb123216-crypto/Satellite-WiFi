package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

// Simple internal notification model
data class ISPNotification(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ISPViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ISPRepository(database.ispDao())

    // UI States
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _selectedRole = MutableStateFlow<String?>(null) // "user" or "admin"
    val selectedRole: StateFlow<String?> = _selectedRole.asStateFlow()

    // Active User (when logged in)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Admin Dashboard User List
    val usersList: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Admin/Global Bills & Devices
    val billsList: StateFlow<List<Billing>> = repository.allBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devicesList: StateFlow<List<Device>> = repository.allDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active User Specific flows (re-bound upon login)
    private val _userBills = MutableStateFlow<List<Billing>>(emptyList())
    val userBills: StateFlow<List<Billing>> = _userBills.asStateFlow()

    private val _userDevices = MutableStateFlow<List<Device>>(emptyList())
    val userDevices: StateFlow<List<Device>> = _userDevices.asStateFlow()

    private val _userWiFiSettings = MutableStateFlow<WiFiSettings?>(null)
    val userWiFiSettings: StateFlow<WiFiSettings?> = _userWiFiSettings.asStateFlow()

    // Notification State
    private val _notifications = MutableStateFlow<List<ISPNotification>>(emptyList())
    val notifications: StateFlow<List<ISPNotification>> = _notifications.asStateFlow()

    // Status Message Toast simulation
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            prepopulateDataIfNeeded()
        }
    }

    fun selectRole(role: String) {
        _selectedRole.value = role
        _authState.value = AuthState.Idle
    }

    fun resetRole() {
        _selectedRole.value = null
        _currentUser.value = null
        _authState.value = AuthState.Idle
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    // Login logic supporting either Local Simulation credentials or standard email inputs
    fun login(email: String, pin: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            // Allow admin bypass for testing
            if (email == "admin@satellite.com" && pin == "123456") {
                val adminUser = repository.getUserByEmail("admin@satellite.com") ?: User(
                    id = "admin_id",
                    name = "Admin Manager",
                    phone = "+91 9876543210",
                    email = "admin@satellite.com",
                    role = "admin",
                    status = "active",
                    plan = "All Access"
                )
                repository.insertUser(adminUser)
                _currentUser.value = adminUser
                _authState.value = AuthState.Success(adminUser)
                showToast("Admin Login Successful!")
                return@launch
            }

            val user = repository.getUserByEmail(email)
            if (user != null) {
                if (user.role != _selectedRole.value) {
                    _authState.value = AuthState.Error("Invalid role for this credentials!")
                    return@launch
                }
                _currentUser.value = user
                _authState.value = AuthState.Success(user)
                bindUserFlows(user.id)
                showToast("Welcome back, ${user.name}!")
            } else {
                _authState.value = AuthState.Error("User not found! Use credentials hint below.")
            }
        }
    }

    // Bind reactive flows for the logged-in user
    private fun bindUserFlows(userId: String) {
        viewModelScope.launch {
            repository.getBillsByUserId(userId).collect {
                _userBills.value = it
            }
        }
        viewModelScope.launch {
            repository.getDevicesByUserId(userId).collect {
                _userDevices.value = it
            }
        }
        viewModelScope.launch {
            repository.getWiFiSettingsFlow(userId).collect {
                _userWiFiSettings.value = it
            }
        }
    }

    // Refresh current user data (e.g. status changes by admin)
    fun refreshCurrentUser() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val updated = repository.getUserById(user.id)
            if (updated != null) {
                _currentUser.value = updated
                // If user status changed to blocked, alert the user
                if (updated.status == "blocked") {
                    showToast("Your account has been blocked by the Administrator.")
                }
            }
        }
    }

    // User actions
    fun payBill(billId: String) {
        viewModelScope.launch {
            repository.updateBillStatus(billId, "paid")
            showToast("Payment Successful! Bill Paid.")
            _currentUser.value?.id?.let { bindUserFlows(it) }
        }
    }

    fun updateWiFiSettings(ssid: String, password: String) {
        val userId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            val settings = WiFiSettings(userId = userId, ssid = ssid, password = password)
            repository.insertWiFiSettings(settings)
            _userWiFiSettings.value = settings
            
            // Also update in Firebase Firestore if configured
            if (FirebaseService.isFirebaseInitialized()) {
                FirebaseService.saveWiFiSettingsToFirestore(userId, ssid, password)
            }
            showToast("WiFi Settings Updated successfully!")
        }
    }

    // Admin actions
    fun addUser(name: String, email: String, phone: String, plan: String, role: String = "user") {
        viewModelScope.launch {
            val newId = "user_" + UUID.randomUUID().toString().take(6)
            val newUser = User(
                id = newId,
                name = name,
                phone = phone,
                email = email,
                role = role,
                status = "active",
                plan = plan
            )
            repository.insertUser(newUser)
            
            // Default WiFi settings
            val wifiSettings = WiFiSettings(
                userId = newId,
                ssid = "SatelliteWiFi_${name.replace(" ", "")}",
                password = "wifi_" + UUID.randomUUID().toString().take(6)
            )
            repository.insertWiFiSettings(wifiSettings)

            // Add standard mobile device
            val defaultDevice = Device(
                id = "dev_" + UUID.randomUUID().toString().take(6),
                userId = newId,
                deviceName = "My Android Phone",
                ipAddress = "192.168.1." + (10..99).random(),
                macAddress = "00:1A:3B:" + (10..99).random() + ":" + (10..99).random() + ":" + (10..99).random(),
                isConnected = true
            )
            repository.insertDevice(defaultDevice)

            // Add an initial unpaid bill
            val initialBill = Billing(
                id = "bill_" + UUID.randomUUID().toString().take(6),
                userId = newId,
                amount = 499.0,
                dueDate = "2026-07-01",
                status = "unpaid"
            )
            repository.insertBill(initialBill)

            // Sync with Firebase
            if (FirebaseService.isFirebaseInitialized()) {
                FirebaseService.saveUserToFirestore(newUser)
                FirebaseService.saveWiFiSettingsToFirestore(newId, wifiSettings.ssid, wifiSettings.password)
                FirebaseService.saveBillToFirestore(initialBill)
            }

            showToast("User '$name' added successfully!")
        }
    }

    fun editUser(userId: String, name: String, email: String, phone: String, plan: String, status: String) {
        viewModelScope.launch {
            val currentUserData = repository.getUserById(userId) ?: return@launch
            val updatedUser = currentUserData.copy(
                name = name,
                email = email,
                phone = phone,
                plan = plan,
                status = status
            )
            repository.insertUser(updatedUser)
            showToast("User '${name}' details updated!")
        }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch {
            repository.deleteUser(user)
            showToast("User '${user.name}' removed from ISP database!")
        }
    }

    fun toggleInternetBlock(userId: String, currentStatus: String) {
        viewModelScope.launch {
            val newStatus = if (currentStatus == "blocked") "active" else "blocked"
            repository.updateUserStatus(userId, newStatus)
            
            // Create notification alert for user
            if (newStatus == "blocked") {
                addNotification(userId, "Internet Suspended", "Your high-speed satellite internet service has been suspended by the administrator due to policy or billing review.")
                showToast("User blocked successfully.")
            } else {
                addNotification(userId, "Internet Restored", "Welcome back! Your high-speed satellite internet service has been fully restored.")
                showToast("User unblocked successfully.")
            }
        }
    }

    fun createBill(userId: String, amount: Double, dueDate: String) {
        viewModelScope.launch {
            val newBill = Billing(
                id = "bill_" + UUID.randomUUID().toString().take(6),
                userId = userId,
                amount = amount,
                dueDate = dueDate,
                status = "unpaid"
            )
            repository.insertBill(newBill)
            
            addNotification(userId, "New Invoice Generated", "An invoice of ₹$amount is due on $dueDate. Please pay inside the app to avoid interruption.")
            showToast("Bill created successfully.")
        }
    }

    fun sendBillReminder(user: User, bill: Billing) {
        viewModelScope.launch {
            addNotification(
                userId = user.id,
                title = "Payment Reminder Alert",
                message = "Dear ${user.name}, this is a reminder that your monthly subscription bill of ₹${bill.amount} is due on ${bill.dueDate}. Please clear dues to ensure non-stop connectivity."
            )
            showToast("Billing reminder alert sent to ${user.name}!")
        }
    }

    fun disconnectDevice(deviceId: String) {
        viewModelScope.launch {
            repository.updateDeviceConnection(deviceId, false)
            showToast("Device connection terminated from access point.")
        }
    }

    fun reconnectDevice(deviceId: String) {
        viewModelScope.launch {
            repository.updateDeviceConnection(deviceId, true)
            showToast("Device connection restored.")
        }
    }

    // Local notification helper
    private fun addNotification(userId: String, title: String, message: String) {
        val newNotif = ISPNotification(userId = userId, title = title, message = message)
        _notifications.value = listOf(newNotif) + _notifications.value
    }

    // Prepulate Room database with highly realistic initial ISP data
    private suspend fun prepopulateDataIfNeeded() {
        val currentUsers = repository.allUsers.first()
        if (currentUsers.isEmpty()) {
            // 1. Create Admin
            val admin = User(
                id = "admin_id",
                name = "ISP Admin Manager",
                phone = "+91 9876543210",
                email = "admin@satellite.com",
                role = "admin",
                status = "active",
                plan = "All Access System"
            )
            repository.insertUser(admin)

            // 2. Create standard user 1 (Satish - active)
            val satish = User(
                id = "satish_user",
                name = "Satish Kumar",
                phone = "9876543210",
                email = "satellitesatish@gmail.com",
                role = "user",
                status = "active",
                plan = "150 Mbps Super Giga"
            )
            repository.insertUser(satish)

            val satishWifi = WiFiSettings(
                userId = "satish_user",
                ssid = "SatelliteWiFi_GigaMax",
                password = "satishgiga123"
            )
            repository.insertWiFiSettings(satishWifi)

            val dev1 = Device("dev1", "satish_user", "Google Pixel 9 Pro", "192.168.1.10", "A1:B2:C3:D4:E5:F6", true)
            val dev2 = Device("dev2", "satish_user", "Asus ROG Laptop", "192.168.1.15", "98:54:11:A2:CC:FF", true)
            val dev3 = Device("dev3", "satish_user", "Living Room SmartTV", "192.168.1.20", "AA:BB:CC:11:22:33", false)
            repository.insertDevice(dev1)
            repository.insertDevice(dev2)
            repository.insertDevice(dev3)

            val bill1 = Billing("bill1", "satish_user", 699.0, "2026-06-01", "paid")
            val bill2 = Billing("bill2", "satish_user", 699.0, "2026-07-05", "unpaid")
            repository.insertBill(bill1)
            repository.insertBill(bill2)

            // 3. Create standard user 2 (Rohit - blocked)
            val rohit = User(
                id = "rohit_user",
                name = "Rohit Sharma",
                phone = "9988776655",
                email = "rohit@gmail.com",
                role = "user",
                status = "blocked",
                plan = "50 Mbps Saver Plan"
            )
            repository.insertUser(rohit)

            val rohitWifi = WiFiSettings(
                userId = "rohit_user",
                ssid = "Rohit_HomeWiFi",
                password = "rohitpassword"
            )
            repository.insertWiFiSettings(rohitWifi)

            val dev4 = Device("dev4", "rohit_user", "iPhone 15", "192.168.1.45", "B2:C3:D4:E5:F6:A1", true)
            repository.insertDevice(dev4)

            val bill3 = Billing("bill3", "rohit_user", 349.0, "2026-05-15", "unpaid")
            repository.insertBill(bill3)

            // Generate some helpful initial notifications
            addNotification("satish_user", "Welcome to Satellite WiFi!", "Your connection is fully active. Enjoy premium high-speed satellite broadband connectivity!")
            addNotification("rohit_user", "Service Suspension Notice", "Your account connection has been suspended. Please check billing or contact ISP administrator.")
        }
    }
}
