package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ISPDao {
    // Users
    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("UPDATE users SET status = :status WHERE id = :userId")
    suspend fun updateUserStatus(userId: String, status: String)

    // Billing
    @Query("SELECT * FROM billing")
    fun getAllBillsFlow(): Flow<List<Billing>>

    @Query("SELECT * FROM billing WHERE userId = :userId")
    fun getBillsByUserIdFlow(userId: String): Flow<List<Billing>>

    @Query("SELECT * FROM billing WHERE userId = :userId")
    suspend fun getBillsByUserId(userId: String): List<Billing>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(billing: Billing)

    @Query("UPDATE billing SET status = :status WHERE id = :billId")
    suspend fun updateBillStatus(billId: String, status: String)

    @Query("DELETE FROM billing WHERE id = :billId")
    suspend fun deleteBillById(billId: String)

    // Devices
    @Query("SELECT * FROM devices")
    fun getAllDevicesFlow(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE userId = :userId")
    fun getDevicesByUserIdFlow(userId: String): Flow<List<Device>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)

    @Query("UPDATE devices SET isConnected = :isConnected WHERE id = :deviceId")
    suspend fun updateDeviceConnection(deviceId: String, isConnected: Boolean)

    @Query("DELETE FROM devices WHERE id = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)

    // WiFi Settings
    @Query("SELECT * FROM wifi_settings WHERE userId = :userId LIMIT 1")
    suspend fun getWiFiSettingsByUserId(userId: String): WiFiSettings?

    @Query("SELECT * FROM wifi_settings WHERE userId = :userId LIMIT 1")
    fun getWiFiSettingsFlow(userId: String): Flow<WiFiSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWiFiSettings(settings: WiFiSettings)
}

@Database(entities = [User::class, Billing::class, Device::class, WiFiSettings::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ispDao(): ISPDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "satellite_wifi_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ISPRepository(private val dao: ISPDao) {
    val allUsers: Flow<List<User>> = dao.getAllUsersFlow()
    val allBills: Flow<List<Billing>> = dao.getAllBillsFlow()
    val allDevices: Flow<List<Device>> = dao.getAllDevicesFlow()

    suspend fun getUserById(userId: String) = dao.getUserById(userId)
    suspend fun getUserByEmail(email: String) = dao.getUserByEmail(email)
    suspend fun insertUser(user: User) = dao.insertUser(user)
    suspend fun deleteUser(user: User) = dao.deleteUser(user)
    suspend fun updateUserStatus(userId: String, status: String) = dao.updateUserStatus(userId, status)

    fun getBillsByUserId(userId: String): Flow<List<Billing>> = dao.getBillsByUserIdFlow(userId)
    suspend fun insertBill(billing: Billing) = dao.insertBill(billing)
    suspend fun updateBillStatus(billId: String, status: String) = dao.updateBillStatus(billId, status)
    suspend fun deleteBillById(billId: String) = dao.deleteBillById(billId)

    fun getDevicesByUserId(userId: String): Flow<List<Device>> = dao.getDevicesByUserIdFlow(userId)
    suspend fun insertDevice(device: Device) = dao.insertDevice(device)
    suspend fun updateDeviceConnection(deviceId: String, isConnected: Boolean) = dao.updateDeviceConnection(deviceId, isConnected)
    suspend fun deleteDeviceById(deviceId: String) = dao.deleteDeviceById(deviceId)

    suspend fun getWiFiSettingsByUserId(userId: String) = dao.getWiFiSettingsByUserId(userId)
    fun getWiFiSettingsFlow(userId: String) = dao.getWiFiSettingsFlow(userId)
    suspend fun insertWiFiSettings(settings: WiFiSettings) = dao.insertWiFiSettings(settings)

    // Helper to prepopulate database with standard mock data if empty
    suspend fun prepopulateIfEmpty() {
        // We will execute this inside the view model or first launch
    }
}
