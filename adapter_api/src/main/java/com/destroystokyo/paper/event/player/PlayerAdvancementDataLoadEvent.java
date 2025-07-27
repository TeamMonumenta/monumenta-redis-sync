package com.destroystokyo.paper.event.player;

import java.io.File;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when the server loads the advancement data for a player
 */
public class PlayerAdvancementDataLoadEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    @Nullable private String jsonData;
    @NotNull private File path;

    public PlayerAdvancementDataLoadEvent(@NotNull Player who, @NotNull File path) {
        super(who);
        this.jsonData = null;
        this.path = path;
    }

    /**
     * Get the file path where advancement data will be loaded from.
     *
     * Data will only be loaded from here if the data is not directly set by {@link #setJsonData}
     *
     * @return advancement data File to load from
     */
    @NotNull
    public File getPath() {
        return path;
    }

    /**
     * Set the file path where advancement data will be loaded from.
     *
     * Data will only be loaded from here if the data is not directly set by {@link #setJsonData}
     *
     * @param path advancement data File to load from
     */
    public void setPath(@NotNull File path) {
        this.path = path;
    }

    /**
     * Get the JSON data supplied by an earlier call to {@link #setJsonData}.
     *
     * This data will be used instead of loading the player's advancement file. It is null unless
     * supplied by a plugin.
     *
     * @return JSON data of the player's advancements as set by {@link #setJsonData}
     */
    @Nullable
    public String getJsonData() {
        return jsonData;
    }

    /**
     * Set the JSON data to use for the player's advancements instead of loading it from a file.
     *
     * This data will be used instead of loading the player's advancement file. It is null unless
     * supplied by a plugin.
     *
     * @param jsonData advancement data JSON string to load. If null, load from file
     */
    public void setJsonData(@Nullable String jsonData) {
        this.jsonData = jsonData;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
