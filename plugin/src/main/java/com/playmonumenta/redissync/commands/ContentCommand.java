package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.ContentAPI;
import com.playmonumenta.redissync.event.SwitchContentEvent;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.LocationArgument;
import dev.jorel.commandapi.arguments.LocationType;
import dev.jorel.commandapi.arguments.RotationArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import dev.jorel.commandapi.wrappers.Rotation;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

public class ContentCommand {
	private static final CommandPermission PERMISSION = CommandPermission.fromString("monumenta.command.content");
	private static final EntitySelectorArgument.ManyPlayers mInvitedPlayersArg = new EntitySelectorArgument.ManyPlayers("invited player");
	private static final StringArgument mContentArg = new StringArgument("content"); // Suggestions replaced below
	private static final LocationArgument mToPosition = new LocationArgument("to position", LocationType.PRECISE_POSITION);
	private static final RotationArgument mToRotation = new RotationArgument("to rotation");

	static {
		mContentArg.replaceSuggestions(ArgumentSuggestions.stringCollection(info ->
			ContentAPI.remoteContent().keySet()));
	}

	@SuppressWarnings("unchecked")
	public static void register() {
		CommandAPICommand pluginCommand = new CommandAPICommand("redissync");

		CommandAPICommand contentCommand = new CommandAPICommand("content")
			.withPermission(PERMISSION)
			.withArguments(
				mInvitedPlayersArg,
				mContentArg
			)
			.withOptionalArguments(
				mToPosition,
				mToRotation
			)
			.executesNative((NativeProxyCommandSender sender, CommandArguments args) -> {
				Collection<Player> invitedPlayers = (Collection<Player>) args.getByArgument(mInvitedPlayersArg);
				String contentId = args.getByArgument(mContentArg);
				Location toPosition = args.getByArgument(mToPosition);
				Rotation toRotation = args.getByArgument(mToRotation);

				run(sender, invitedPlayers, contentId, toPosition, toRotation);
			});
		contentCommand.register();

		new CommandAPICommand("monumenta")
			.withSubcommand(pluginCommand
				.withSubcommand(contentCommand))
			.register();
	}

	public static void run(
		NativeProxyCommandSender sender,
		Collection<Player> invitedPlayers,
		String contentId,
		@Nullable Location toLocation,
		@Nullable Rotation toRotation
	) throws WrapperCommandSyntaxException {
		if (!(sender.getCallee() instanceof Player player)) {
			throw CommandAPI.failWithString("This command must be run as the main player to be transferred");
		}
		UUID mainPlayerId = player.getUniqueId();

		Collection<UUID> invitedPlayerIds = invitedPlayers.stream().map(Entity::getUniqueId).toList();

		Location fromLoc = sender.getLocation();
		Vector fromPos = fromLoc.toVector();
		float fromYaw = fromLoc.getYaw();
		float fromPitch = fromLoc.getPitch();
		Vector2f fromRotationVec = new Vector2f(fromYaw, fromPitch);

		Vector toPos = toLocation == null ? null : toLocation.toVector();
		Vector2f toRotationVec = toRotation == null ? null : new Vector2f(toRotation.getYaw(), toRotation.getPitch());

		new SwitchContentEvent(
			mainPlayerId,
			invitedPlayerIds,
			contentId,
			fromPos,
			fromRotationVec,
			toPos,
			toRotationVec
		).callEvent();
	}
}
