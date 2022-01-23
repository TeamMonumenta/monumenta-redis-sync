package com.playmonumenta.redissync.adapters;

import java.io.IOException;

import com.google.gson.JsonObject;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

public interface VersionAdapter {
	class ReturnParams {
		public final Location mReturnLoc;
		public final Float mReturnYaw;
		public final Float mReturnPitch;

		public ReturnParams(Location returnLoc, Float returnYaw, Float returnPitch) {
			mReturnLoc = returnLoc;
			mReturnYaw = returnYaw;
			mReturnPitch = returnPitch;
		}
	}

	class SaveData {
		private final byte[] mData;
		private final String mShardData;

		protected SaveData(byte[] data, String shardData) {
			mData = data;
			mShardData = shardData;
		}

		public byte[] getData() {
			return mData;
		}

		public String getShardData() {
			return mShardData;
		}
	}

	JsonObject getPlayerScoresAsJson(String playerName, Scoreboard scoreboard);

	void resetPlayerScores(String playerName, Scoreboard scoreboard);

	Object retrieveSaveData(byte[] data, JsonObject shardData) throws IOException;

	SaveData extractSaveData(Object nbtObj, ReturnParams returnParams) throws IOException;

	void savePlayer(Player player) throws Exception;

	Object upgradePlayerData(Object nbtTagCompound);

	String upgradePlayerAdvancements(String advancementsStr) throws Exception;
}
