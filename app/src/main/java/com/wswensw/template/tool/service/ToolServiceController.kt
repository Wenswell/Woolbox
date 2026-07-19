package com.wswensw.template.tool.service

import com.wswensw.template.tool.model.OledShieldConfig
import java.lang.ref.WeakReference

sealed interface ExternalToolCommand {
    data object GLOBAL_COPY : ExternalToolCommand

    data object OLED_SHIELD : ExternalToolCommand

    data class OledShieldProfile(
        val packageName: String,
        val config: OledShieldConfig,
    ) : ExternalToolCommand {
        init {
            require(packageName.isNotBlank()) { "Package name must not be blank" }
        }
    }
}

enum class ToolLaunchResult {
    Accepted,
    ServiceUnavailable,
    TargetAppUnavailable,
    TargetLaunchFailed,
}

object ToolServiceController {
    private var serviceReference: WeakReference<ToolAccessibilityService>? = null

    fun attach(service: ToolAccessibilityService) {
        serviceReference = WeakReference(service)
    }

    fun detach(service: ToolAccessibilityService) {
        if (serviceReference?.get() === service) {
            serviceReference = null
        }
    }

    fun isConnected(): Boolean = serviceReference?.get() != null

    fun execute(command: ExternalToolCommand): ToolLaunchResult {
        val service = serviceReference?.get() ?: return ToolLaunchResult.ServiceUnavailable
        service.executeCommand(command)
        return ToolLaunchResult.Accepted
    }
}
