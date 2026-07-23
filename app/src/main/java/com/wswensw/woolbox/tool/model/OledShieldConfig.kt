package com.wswensw.woolbox.tool.model

data class OledShieldConfig(
    val opacityPercent: Int = 100,
    val widthPercent: Int = 100,
    val heightPercent: Int = 15,
    val panelXPercent: Float = 0.08f,
    val panelYPercent: Float = 0.45f,
) {
    fun normalized(): OledShieldConfig = copy(
        opacityPercent = opacityPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
        widthPercent = widthPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
        heightPercent = heightPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
        panelXPercent = panelXPercent.coerceIn(0f, 1f),
        panelYPercent = panelYPercent.coerceIn(0f, 1f),
    )

    companion object {
        const val MIN_PERCENT = 1
        const val MAX_PERCENT = 100
        val Default = OledShieldConfig()
    }
}

data class OledShieldProfile(
    val packageName: String,
    val config: OledShieldConfig,
    val recordedAt: Long,
)
