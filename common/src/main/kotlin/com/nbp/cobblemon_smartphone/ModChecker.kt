@file:JvmName("ModChecker")
package com.nbp.cobblemon_smartphone

import dev.architectury.injectables.annotations.ExpectPlatform

@ExpectPlatform
fun isModLoaded(modId: String): Boolean {
    // Este código nunca deve ser executado; a implementação específica da plataforma será injetada.
    throw AssertionError("Not found platform implement!")
}

/** Display name of the mod with the given id, or null when it is not a loaded mod. */
@ExpectPlatform
fun getModName(modId: String): String? {
    // Este código nunca deve ser executado; a implementação específica da plataforma será injetada.
    throw AssertionError("Not found platform implement!")
}
