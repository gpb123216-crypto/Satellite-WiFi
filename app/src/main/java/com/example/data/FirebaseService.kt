package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FirebaseService {
    private const val TAG = "FirebaseService"

    fun isFirebaseInitialized(): Boolean {
        return try {
            val app = FirebaseApp.getInstance()
            app != null
        } catch (e: Exception) {
            Log.w(TAG, "Firebase App is not initialized. Falling back to Local Room Database mode.")
            false
        }
    }

    fun getAuth(): FirebaseAuth? {
        return if (isFirebaseInitialized()) {
            FirebaseAuth.getInstance()
        } else {
            null
        }
    }

    fun getFirestore(): FirebaseFirestore? {
        return if (isFirebaseInitialized()) {
            FirebaseFirestore.getInstance()
        } else {
            null
        }
    }

    suspend fun saveUserToFirestore(user: User): Boolean {
        val db = getFirestore() ?: return false
        return try {
            db.collection("users").document(user.id).set(user).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user to Firestore: ${e.message}")
            false
        }
    }

    suspend fun getUserFromFirestore(userId: String): User? {
        val db = getFirestore() ?: return null
        return try {
            val doc = db.collection("users").document(userId).get().await()
            if (doc.exists()) {
                User(
                    id = doc.getString("id") ?: userId,
                    name = doc.getString("name") ?: "",
                    phone = doc.getString("phone") ?: "",
                    email = doc.getString("email") ?: "",
                    role = doc.getString("role") ?: "user",
                    status = doc.getString("status") ?: "active",
                    plan = doc.getString("plan") ?: "100 Mbps Standard"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user from Firestore: ${e.message}")
            null
        }
    }

    suspend fun saveWiFiSettingsToFirestore(userId: String, ssid: String, password: String): Boolean {
        val db = getFirestore() ?: return false
        return try {
            val settings = mapOf("userId" to userId, "ssid" to ssid, "password" to password)
            db.collection("wifi_settings").document(userId).set(settings).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving WiFi settings to Firestore: ${e.message}")
            false
        }
    }

    suspend fun saveBillToFirestore(bill: Billing): Boolean {
        val db = getFirestore() ?: return false
        return try {
            db.collection("billing").document(bill.id).set(bill).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving Bill to Firestore: ${e.message}")
            false
        }
    }
}
