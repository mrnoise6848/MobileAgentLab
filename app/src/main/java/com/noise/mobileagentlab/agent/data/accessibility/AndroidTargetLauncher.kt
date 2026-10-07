package com.noise.mobileagentlab.agent.data.accessibility

import android.content.Context
import android.content.Intent
import com.noise.mobileagentlab.agent.domain.port.TargetLauncher
import com.noise.mobileagentlab.agent.domain.safety.SafetyPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 12 — brings the allowlisted target app to the foreground before a run.
 *
 * The allowlist is enforced here as well: launching is only attempted for
 * packages the policy was configured with, so the agent can never warm-start an
 * arbitrary application.
 */
class AndroidTargetLauncher(
    context: Context,
    override val supportedPackages: Set<String> = setOf(SafetyPolicy.DEMO_TARGET_PACKAGE),
) : TargetLauncher {

    private val appContext = context.applicationContext

    override suspend fun bringToFront(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (packageName !in supportedPackages) return@withContext false
            val intent = appContext.packageManager.getLaunchIntentForPackage(packageName)
                ?: return@withContext false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                appContext.startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }
}
