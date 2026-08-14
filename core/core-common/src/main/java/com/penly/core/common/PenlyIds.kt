package com.penly.core.common

import java.util.UUID

object PenlyIds {
    fun newId(): String = UUID.randomUUID().toString()
}
