package com.nbp.cobblemon_smartphone.util;

import net.minecraft.nbt.CompoundTag;

public interface PreferencesSaver {
    String SAVED_PREFERENCES_KEY = "cobblemonsmartphone_saved_preferences";
    String BUCKET_INDEX_KEY = "bucket_index";
    String SORTING_KEY = "sorting_key";
    String ACTION_ORDER_KEY = "action_order";
    String QUICK_ACTIONS_KEY = "quick_actions";

    CompoundTag cobblemonsmartphone$getSavedPreferences();
}
