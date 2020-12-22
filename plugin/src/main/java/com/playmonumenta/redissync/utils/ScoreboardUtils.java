package com.playmonumenta.redissync.utils;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

public class ScoreboardUtils {
	@Deprecated
	/* Use the method in the VersionAdapter instead */
	public static JsonObject getAsJsonObject(Player player) {
		JsonObject data = new JsonObject();

		for (Objective objective : Bukkit.getScoreboardManager().getMainScoreboard().getObjectives()) {
			Score score = objective.getScore(player.getName());
			if (score != null) {
				data.addProperty(objective.getName(), score.getScore());
			}
		}

		return data;
	}

	public static void loadFromJsonObject(Player player, JsonObject data) {
		Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

		for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
			String name = entry.getKey();
			int scoreVal = entry.getValue().getAsInt();

			Objective objective = scoreboard.getObjective(name);
			if (objective == null) {
				objective = scoreboard.registerNewObjective(name, "dummy", name);
			}

			Score score = objective.getScore(player.getName());
			score.setScore(scoreVal);
		}
	}

	public static int getScoreboardValue(Entity entity, String scoreboardValue) {
		Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(scoreboardValue);
		if (objective != null) {
			if (entity instanceof Player) {
				return objective.getScore(entity.getName()).getScore();
			} else {
				return objective.getScore(entity.getUniqueId().toString()).getScore();
			}
		}

		return 0;
	}

	public static void setScoreboardValue(Entity entity, String scoreboardValue, int value) {
		Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(scoreboardValue);
		if (objective != null) {
			final Score score;
			if (entity instanceof Player) {
				score = objective.getScore(entity.getName());
			} else {
				score = objective.getScore(entity.getUniqueId().toString());
			}
			score.setScore(value);
		}
	}
}
