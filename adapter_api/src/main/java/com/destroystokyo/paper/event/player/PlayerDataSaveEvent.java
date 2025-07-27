package com.destroystokyo.paper.event.player;

import java.io.File;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when the server saves the primary .dat data for a player
 */
public class PlayerDataSaveEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    @NotNull private final Object data;
    @NotNull private File path;
    private boolean cancel = false;

    public PlayerDataSaveEvent(@NotNull Player who, @NotNull File path, @NotNull Object data) {
        super(who);
        this.data = data;
        this.path = path;
    }

    /**
     * Get the file path where player data will be saved to.
     *
     * @return player data File to save to
     */
    @NotNull
    public File getPath() {
        return path;
    }

    /**
     * Set the file path where player data will be saved to.
     */
    public void setPath(@NotNull File path) {
        this.path = path;
    }

    /**
     * Get the NBTTagCompound player data that will be saved.
     *
     * @return NBTTagCompound player data
     */
    @NotNull
    public Object getData() {
        return data;
    }

    public boolean isCancelled() {
        return cancel;
    }

    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
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
