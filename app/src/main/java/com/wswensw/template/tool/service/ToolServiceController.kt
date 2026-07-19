package com.wswensw.template.tool.service

import java.lang.ref.WeakReference

enum class ExternalToolCommand {
    GLOBAL_COPY,
    OLED_SHIELD,
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

    fun execute(command: ExternalToolCommand) {
        serviceReference?.get()?.executeCommand(command)
    }
}
