package com.penly.core.model

import kotlinx.serialization.Serializable

/**
 * Data-driven page background templates (plan §29). Plain enum — core-model must stay free of
 * Compose/Activity dependencies; rendering lives in core-renderer ([com.penly.core.renderer]).
 */
@Serializable
enum class PageTemplate {
    BLANK,
    RULED,
    NARROW_RULED,
    GRID,
    DOTS,
    CORNELL,
}
