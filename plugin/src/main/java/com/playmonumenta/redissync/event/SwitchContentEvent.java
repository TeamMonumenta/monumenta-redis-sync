package com.playmonumenta.redissync.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

public class SwitchContentEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final String mContentId;
	private final @Nullable Vector mFromPosition;
	private final @Nullable Vector2f mFromRotation;
	private final @Nullable Vector mToPosition;
	private final @Nullable Vector2f mToRotation;

	public SwitchContentEvent(
		String contentId,
		@Nullable Vector fromPosition,
		@Nullable Vector2f fromRotation,
		@Nullable Vector toPosition,
		@Nullable Vector2f toRotation
	) {
		mContentId = contentId;
		mFromPosition = fromPosition;
		mFromRotation = fromRotation;
		mToPosition = toPosition;
		mToRotation = toRotation;
	}

	public String content() {
		return mContentId;
	}

	public Vector fromPosition() {
		return mFromPosition;
	}

	public Vector2f fromRotation() {
		return mFromRotation;
	}

	public Vector toPosition() {
		return mToPosition;
	}

	public Vector2f toRotation() {
		return mToRotation;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
