package dev.chipsxo.chipscritters;

import com.google.gson.JsonObject;

/** Old Fastest history/profile keys stay archived; a faster, incomplete tour is never a Money PB. */
final class RetiredModeMigration {
    static boolean migrate(JsonObject config){
        var mode=config.get("mode");
        if(mode==null||!mode.isJsonPrimitive()||!"FASTEST".equals(mode.getAsString()))return false;
        config.addProperty("mode","MONEY");return true;
    }
}
