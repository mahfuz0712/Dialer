package com.mux.phone.dialer.call

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import androidx.annotation.RequiresApi
import com.mux.phone.dialer.util.SimOption

/** Everything that touches android.telecom (API 23+). Never loaded on Android 5.x. */
@RequiresApi(23)
object TelecomHelper {
    fun isDefault(ctx: Context): Boolean = try {
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.defaultDialerPackage == ctx.packageName
    } catch (e: Exception) {
        false
    }

    fun simOptions(ctx: Context): List<SimOption> = try {
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.callCapablePhoneAccounts.mapIndexed { i, h ->
            val label = tm.getPhoneAccount(h)?.label?.toString().orEmpty().uppercase()
            SimOption(if (label.isBlank()) "SIM ${i + 1}" else "SIM ${i + 1} · $label", h)
        }
    } catch (e: SecurityException) {
        emptyList()
    }

    /** Intent that asks the user to make Dialer the default phone app (null if already / not possible). */
    fun requestIntent(ctx: Context): Intent? {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java) ?: return null
            return if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER))
                rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            else null
        }
        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, ctx.packageName)
    }
}
