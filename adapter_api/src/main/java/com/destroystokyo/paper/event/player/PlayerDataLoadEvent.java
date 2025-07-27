package com.destroystokyo.paper.event.player;

import java.io.File;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when the server loads the playerdata data for a player
 */
public class PlayerDataLoadEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    @Nullable private Object data;
    @NotNull private File path;

    public PlayerDataLoadEvent(@NotNull Player who, @NotNull File path) {
        super(who);
        this.data = null;
        this.path = path;
    }

    /**
     * Get the file path where data will be loaded from.
     * <p>
     * Data will only be loaded from here if the data is not directly set by {@link #setData}
     *
     * @return data File to load from
     */
    @NotNull
    public File getPath() {
        return path;
    }

    /**
     * Set the file path where data will be loaded from.
     * <p>
     * Data will only be loaded from here if the data is not directly set by {@link #setData}
     *
     * @param path data File to load from
     */
    public void setPath(@NotNull File path) {
        this.path = path;
    }

    /**
     * Get the data supplied by an earlier call to {@link #setData}.
     * <p>
     * This data will be used instead of loading the player's file. It is null unless
     * supplied by a plugin.
     *
     * @return NBTTagCompound data of the player's .dat file as set by {@link #setData}
     */
    @Nullable
    public Object getData() {
        return data;
    }

    /**
     * Set the data to use for the player's data instead of loading it from a file.
     * <p>
     * This data will be used instead of loading the player's .dat file. It is null unless
     * supplied by a plugin.
     *
     * @param data NBTTagCompound data to load. If null, load from file
     */
    public void setData(@Nullable Object data) {
        this.data = data;
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
