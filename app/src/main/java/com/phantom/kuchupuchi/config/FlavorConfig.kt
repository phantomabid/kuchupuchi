package com.phantom.kuchupuchi.config

import com.phantom.kuchupuchi.BuildConfig
import com.phantom.kuchupuchi.R
import java.util.Locale

object FlavorConfig {
    val flavorName: String = BuildConfig.FLAVOR_NAME
    val partnerFlavorName: String = BuildConfig.PARTNER_FLAVOR_NAME
    val firebaseDbUrl: String = BuildConfig.FIREBASE_DB_URL

    fun getSelfDbPath(): String = BuildConfig.SELF_DB_PATH
    fun getPartnerDbPath(): String = BuildConfig.PARTNER_DB_PATH

    @get:JvmName("getSelfDbPathProperty")
    val selfDbPath: String = BuildConfig.SELF_DB_PATH

    @get:JvmName("getPartnerDbPathProperty")
    val partnerDbPath: String = BuildConfig.PARTNER_DB_PATH

    fun getSelfIconResId(status: String = "active"): Int {
        val isKuchu = flavorName.equals("kuchu", ignoreCase = true)
        return getBirdIconResId(isYellowBird = isKuchu, status = status)
    }

    fun getPartnerIconResId(status: String = "active"): Int {
        val isPartnerKuchu = partnerFlavorName.equals("kuchu", ignoreCase = true)
        return getBirdIconResId(isYellowBird = isPartnerKuchu, status = status)
    }

    private fun getBirdIconResId(isYellowBird: Boolean, status: String): Int {
        return if (isYellowBird) {
            when (status.lowercase(Locale.ROOT)) {
                "active" -> R.drawable.ic_yellow_bird_active
                "away" -> R.drawable.ic_yellow_bird_away
                "offline" -> R.drawable.ic_yellow_bird_offline
                else -> R.drawable.ic_yellow_bird_offline
            }
        } else {
            when (status.lowercase(Locale.ROOT)) {
                "active" -> R.drawable.ic_blue_bird_active
                "away" -> R.drawable.ic_blue_bird_away
                "offline" -> R.drawable.ic_blue_bird_offline
                else -> R.drawable.ic_blue_bird_offline
            }
        }
    }

    fun getIconResId(): Int {
        return getSelfIconResId("active")
    }
}
