package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.domain.AppUpdateInfo
import com.example.domain.MasjidSchedule
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class RemoteMainDocument(
    val schedule: MasjidSchedule,
    val updateInfo: AppUpdateInfo?
)

class RemoteScheduleSource(context: Context) {

    private val appContext = context.applicationContext
    private val firestore: FirebaseFirestore by lazy {
        val dbId = appContext.getString(R.string.firestore_database_id).ifBlank { "(default)" }
        FirebaseFirestore.getInstance(dbId)
    }

    fun observeMainSchedule(): Flow<MasjidSchedule?> =
        observeMainDocument().map { it?.schedule }

    fun observeMainDocument(): Flow<RemoteMainDocument?> = callbackFlow {
        val docRef = firestore.collection(COLLECTION_SCHEDULES).document(DOC_MAIN)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Firestore schedule listener error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                trySend(snapshot.toRemoteMainDocument())
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun fetchOnce(timeoutMs: Long = 4_000L): MasjidSchedule? =
        fetchMainDocumentOnce(timeoutMs)?.schedule

    suspend fun fetchMainDocumentOnce(timeoutMs: Long = 4_000L): RemoteMainDocument? {
        return try {
            withTimeoutOrNull(timeoutMs) {
                val snapshot = firestore.collection(COLLECTION_SCHEDULES)
                    .document(DOC_MAIN)
                    .get()
                    .await()
                if (snapshot.exists()) snapshot.toRemoteMainDocument() else null
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Firestore one-shot fetch skipped: ${e.message}")
            null
        }
    }

    private fun DocumentSnapshot.toRemoteMainDocument(): RemoteMainDocument {
        val map = data ?: emptyMap<String, Any>()
        fun str(key: String, fallback: String = ""): String =
            (map[key] as? String)?.trim() ?: fallback

        fun intVal(key: String, fallback: Int): Int =
            (map[key] as? Number)?.toInt()?.coerceIn(0, 120) ?: fallback

        val schedule = MasjidSchedule(
            masjidName = str("masjidName", "My Masjid").ifBlank { "My Masjid" },
            cityLabel = str("cityLabel"),
            announcement = str("announcement"),
            fajrAzan = str("fajrAzan"),
            dhuhrAzan = str("dhuhrAzan"),
            asrAzan = str("asrAzan"),
            maghribAzan = str("maghribAzan"),
            ishaAzan = str("ishaAzan"),
            fajrJamaatOffsetMin = intVal("fajrJamaatOffsetMin", 15),
            dhuhrJamaatOffsetMin = intVal("dhuhrJamaatOffsetMin", 15),
            asrJamaatOffsetMin = intVal("asrJamaatOffsetMin", 15),
            maghribJamaatOffsetMin = intVal("maghribJamaatOffsetMin", 5),
            ishaJamaatOffsetMin = intVal("ishaJamaatOffsetMin", 15),
            fajrJamaatOverride = str("fajrJamaatOverride"),
            dhuhrJamaatOverride = str("dhuhrJamaatOverride"),
            asrJamaatOverride = str("asrJamaatOverride"),
            maghribJamaatOverride = str("maghribJamaatOverride"),
            ishaJamaatOverride = str("ishaJamaatOverride")
        )

        val updateCode = (map["updateVersionCode"] as? Number)?.toInt() ?: 0
        val updateName = str("updateVersionName")
        val updateUrl = str("updateApkUrl")
        val updateInfo = if ((updateCode > 0 || updateName.isNotBlank()) && updateUrl.isNotBlank()) {
            AppUpdateInfo(
                versionCode = updateCode,
                versionName = updateName,
                notes = str("updateNotes"),
                apkUrl = updateUrl
            )
        } else {
            null
        }

        return RemoteMainDocument(schedule = schedule, updateInfo = updateInfo)
    }

    companion object {
        private const val TAG = "RemoteScheduleSource"
        private const val COLLECTION_SCHEDULES = "schedules"
        private const val DOC_MAIN = "main"
    }
}
