package de.tb.lumen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Nach dem Booten und nach jedem Update: Dienste reparieren; nach dem Booten Lumen starten. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                SystemSettings.selfHeal(context)
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> SystemSettings.selfHeal(context)
        }
    }
}
