package com.playmonumenta.redissync.adapters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R3.scoreboard.CraftScoreboard;
import org.bukkit.entity.Player;

import net.minecraft.server.v1_16_R3.AdvancementDataPlayer;
import net.minecraft.server.v1_16_R3.DataFixTypes;
import net.minecraft.server.v1_16_R3.EntityPlayer;
import net.minecraft.server.v1_16_R3.GameProfileSerializer;
import net.minecraft.server.v1_16_R3.NBTCompressedStreamTools;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import net.minecraft.server.v1_16_R3.NBTTagDouble;
import net.minecraft.server.v1_16_R3.NBTTagFloat;
import net.minecraft.server.v1_16_R3.NBTTagList;
import net.minecraft.server.v1_16_R3.PlayerList;
import net.minecraft.server.v1_16_R3.Scoreboard;
import net.minecraft.server.v1_16_R3.ScoreboardObjective;
import net.minecraft.server.v1_16_R3.ScoreboardScore;
import net.minecraft.server.v1_16_R3.SharedConstants;

public class VersionAdapter_v1_16_R3 implements VersionAdapter {
	private static Gson advancementsGson = null;

	private Method mSaveMethod = null;
	private final Logger mLogger;

	public VersionAdapter_v1_16_R3(Logger logger) {
		mLogger = logger;
	}

	@SuppressWarnings("unchecked")
	public JsonObject getPlayerScoresAsJson(String playerName, org.bukkit.scoreboard.Scoreboard scoreboard) {
		Scoreboard nmsScoreboard = ((CraftScoreboard)scoreboard).getHandle();

		JsonObject data = new JsonObject();
		Map<String, Map<ScoreboardObjective, ScoreboardScore>> playerScores = null;

		try {
			Field playerScoresField = Scoreboard.class.getDeclaredField("playerScores");
			playerScoresField.setAccessible(true);
			playerScores = (Map<String, Map<ScoreboardObjective, ScoreboardScore>>)playerScoresField.get(nmsScoreboard);
		} catch (NoSuchFieldException | IllegalAccessException ex) {
			mLogger.severe("Failed to access playerScores scoreboard field: " + ex.getMessage());
			ex.printStackTrace();
			return data;
		}

		Map<ScoreboardObjective, ScoreboardScore> scores = playerScores.get(playerName);
		if (scores == null) {
			// No scores for this player
			return data;
		}

		for (Map.Entry<ScoreboardObjective, ScoreboardScore> entry : scores.entrySet()) {
			data.addProperty(entry.getKey().getName(), entry.getValue().getScore());
		}

		return data;
	}

	public void resetPlayerScores(String playerName, org.bukkit.scoreboard.Scoreboard scoreboard) {
		Scoreboard nmsScoreboard = ((CraftScoreboard)scoreboard).getHandle();
		nmsScoreboard.resetPlayerScores(playerName, null);
	}

	public Object retrieveSaveData(byte[] data, @Nullable JsonObject shardData) throws IOException {
		ByteArrayInputStream inBytes = new ByteArrayInputStream(data);
		NBTTagCompound nbt = NBTCompressedStreamTools.a(inBytes);

		applyInt(shardData, nbt, "SpawnX");
		applyInt(shardData, nbt, "SpawnY");
		applyInt(shardData, nbt, "SpawnZ");
		applyBool(shardData, nbt, "SpawnForced");
		applyFloat(shardData, nbt, "SpawnAngle");
		applyStr(shardData, nbt, "SpawnDimension");
		// flying is nested in the abilities structure
		if (shardData.has("flying")) {
			final NBTTagCompound abilities;
			if (nbt.hasKey("abilities")) {
				abilities = nbt.getCompound("abilities");
			} else {
				abilities = new NBTTagCompound();
				nbt.set("abilities", abilities);
			}
			abilities.setBoolean("flying", shardData.get("flying").getAsBoolean());
		}
		applyBool(shardData, nbt, "FallFlying");
		applyFloat(shardData, nbt, "FallDistance");
		applyBool(shardData, nbt, "OnGround");
		applyInt(shardData, nbt, "Dimension");
		applyStr(shardData, nbt, "world");
		applyLong(shardData, nbt, "WorldUUIDMost");
		applyLong(shardData, nbt, "WorldUUIDLeast");
		applyDoubleList(shardData, nbt, "Pos");
		applyDoubleList(shardData, nbt, "Motion");
		applyFloatList(shardData, nbt, "Rotation");
		applyDoubleList(shardData, nbt, "Paper.Origin");
		applyCompoundOfDoubles(shardData, nbt, "enteredNetherPosition");

		return nbt;
	}

	public SaveData extractSaveData(Object nbtObj, @Nullable VersionAdapter.ReturnParams returnParams) throws IOException {
		NBTTagCompound nbt = (NBTTagCompound) nbtObj;

		JsonObject obj = new JsonObject();
		copyInt(obj, nbt, "SpawnX");
		copyInt(obj, nbt, "SpawnY");
		copyInt(obj, nbt, "SpawnZ");
		copyBool(obj, nbt, "SpawnForced");
		copyFloat(obj, nbt, "SpawnAngle");
		copyStr(obj, nbt, "SpawnDimension");
		// flying is nested in the abilities structure
		if (nbt.hasKey("abilities")) {
			NBTTagCompound abilities = nbt.getCompound("abilities");
			copyBool(obj, abilities, "flying");
		}
		copyBool(obj, nbt, "FallFlying");
		copyFloat(obj, nbt, "FallDistance");
		copyBool(obj, nbt, "OnGround");
		copyInt(obj, nbt, "Dimension");
		copyStr(obj, nbt, "world");
		copyLong(obj, nbt, "WorldUUIDMost");
		copyLong(obj, nbt, "WorldUUIDLeast");
		copyDoubleList(obj, nbt, "Pos");
		copyDoubleList(obj, nbt, "Motion");
		copyFloatList(obj, nbt, "Rotation");
		copyDoubleList(obj, nbt, "Paper.Origin");
		copyCompoundOfDoubles(obj, nbt, "enteredNetherPosition");

		if (returnParams != null && returnParams.mReturnLoc != null) {
			JsonArray arr = new JsonArray();
			arr.add(returnParams.mReturnLoc.getX());
			arr.add(returnParams.mReturnLoc.getY());
			arr.add(returnParams.mReturnLoc.getZ());
			obj.remove("Pos");
			obj.add("Pos", arr);
		}

		if (returnParams != null && returnParams.mReturnPitch != null && returnParams.mReturnYaw != null) {
			JsonArray arr = new JsonArray();
			arr.add(returnParams.mReturnYaw);
			arr.add(returnParams.mReturnPitch);
			obj.remove("Rotation");
			obj.add("Rotation", arr);
		}

		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
		NBTCompressedStreamTools.a(nbt, outBytes);
		return new SaveData(outBytes.toByteArray(), obj.toString());
	}

	public void savePlayer(Player player) throws Exception {
		PlayerList playerList = ((CraftServer)Bukkit.getServer()).getHandle();

		if (mSaveMethod == null) {
			mSaveMethod = PlayerList.class.getDeclaredMethod("savePlayerFile", EntityPlayer.class);
			mSaveMethod.setAccessible(true);
		}

		mSaveMethod.invoke(playerList, ((CraftPlayer)player).getHandle());
	}

	public Object upgradePlayerData(Object nbtTagCompound) {
		NBTTagCompound nbt = (NBTTagCompound) nbtTagCompound;
		int i = nbt.hasKeyOfType("DataVersion", 3) ? nbt.getInt("DataVersion") : -1;
		DataFixer dataFixer = ((CraftServer)Bukkit.getServer()).getHandle().getServer().dataConverterManager;
		nbt = GameProfileSerializer.a(dataFixer, DataFixTypes.PLAYER, nbt, i);
		nbt.setInt("DataVersion", SharedConstants.getGameVersion().getWorldVersion());
		return nbt;
	}

	public String upgradePlayerAdvancements(String advancementsStr) throws Exception {
		JsonReader jsonreader = new JsonReader(new StringReader(advancementsStr));
		jsonreader.setLenient(false);
		Dynamic<JsonElement> dynamic = new Dynamic<>(JsonOps.INSTANCE, Streams.parse(jsonreader));

		if (!dynamic.get("DataVersion").asNumber().result().isPresent()) {
			dynamic = dynamic.set("DataVersion", dynamic.createInt(1343));
		}

		DataFixer dataFixer = ((CraftServer)Bukkit.getServer()).getHandle().getServer().dataConverterManager;
		dynamic = dataFixer.update(DataFixTypes.ADVANCEMENTS.a(), dynamic, dynamic.get("DataVersion").asInt(0), SharedConstants.getGameVersion().getWorldVersion());
		dynamic = dynamic.remove("DataVersion");

		if (advancementsGson == null) {
			Field gsonField = AdvancementDataPlayer.class.getDeclaredField("b");
			gsonField.setAccessible(true);
			advancementsGson = (Gson)gsonField.get(null);
		}

		JsonElement element = dynamic.getValue();
		element.getAsJsonObject().addProperty("DataVersion", SharedConstants.getGameVersion().getWorldVersion());

		return advancementsGson.toJson(element);
	}

	protected NBTTagList toDoubleList(double... doubles) {
		NBTTagList nbttaglist = new NBTTagList();

		for (double d : doubles) {
			nbttaglist.add(NBTTagDouble.a(d));
		}

		return nbttaglist;
	}

	private void applyStr(JsonObject obj, NBTTagCompound nbt, String key) {
		if (obj.has(key)) {
			nbt.setString(key, obj.get(key).getAsString());
		}
	}

	private void applyInt(JsonObject obj, NBTTagCompound nbt, String key) {
		if (obj.has(key)) {
			nbt.setInt(key, obj.get(key).getAsInt());
		}
	}

	private void applyLong(JsonObject obj, NBTTagCompound nbt, String key) {
		if (obj.has(key)) {
			nbt.setLong(key, obj.get(key).getAsLong());
		}
	}

	private void applyFloat(JsonObject obj, NBTTagCompound nbt, String key) {
		if (obj.has(key)) {
			nbt.setFloat(key, obj.get(key).getAsFloat());
		}
	}

	private void applyBool(JsonObject obj, NBTTagCompound nbt, String key) {
		if (obj.has(key)) {
			nbt.setBoolean(key, obj.get(key).getAsBoolean());
		}
	}

	private void applyFloatList(JsonObject obj, NBTTagCompound nbt, String key) {
		if (obj.has(key)) {
			JsonElement element = obj.get(key);
			if (element.isJsonArray()) {
				NBTTagList nbttaglist = new NBTTagList();
				for (JsonElement val : element.getAsJsonArray()) {
					nbttaglist.add(NBTTagFloat.a(val.getAsFloat()));
				}
				nbt.set(key, nbttaglist);
			}
		}
	}

	private void applyDoubleList(JsonObject obj, NBTTagCompound nbt, String key) {
		if (obj.has(key)) {
			JsonElement element = obj.get(key);
			if (element.isJsonArray()) {
				NBTTagList nbttaglist = new NBTTagList();
				for (JsonElement val : element.getAsJsonArray()) {
					nbttaglist.add(NBTTagDouble.a(val.getAsDouble()));
				}
				nbt.set(key, nbttaglist);
			}
		}
	}

	private void applyCompoundOfDoubles(JsonObject obj, NBTTagCompound nbt, String key) {
		if (obj.has(key)) {
			JsonElement element = obj.get(key);
			if (element.isJsonObject()) {
				NBTTagCompound nbtcomp = new NBTTagCompound();
				for (Map.Entry<String, JsonElement> subentry : element.getAsJsonObject().entrySet()) {
					nbtcomp.setDouble(subentry.getKey(), subentry.getValue().getAsDouble());
				}
				nbt.set(key, nbtcomp);
			}
		}
	}

	private void copyStr(JsonObject obj, NBTTagCompound nbt, String key) {
		if (nbt.hasKey(key)) {
			obj.addProperty(key, nbt.getString(key));
			nbt.remove(key);
		}
	}

	private void copyInt(JsonObject obj, NBTTagCompound nbt, String key) {
		if (nbt.hasKey(key)) {
			obj.addProperty(key, nbt.getInt(key));
			nbt.remove(key);
		}
	}

	private void copyLong(JsonObject obj, NBTTagCompound nbt, String key) {
		if (nbt.hasKey(key)) {
			obj.addProperty(key, nbt.getLong(key));
			nbt.remove(key);
		}
	}

	private void copyFloat(JsonObject obj, NBTTagCompound nbt, String key) {
		if (nbt.hasKey(key)) {
			obj.addProperty(key, nbt.getFloat(key));
			nbt.remove(key);
		}
	}

	private void copyBool(JsonObject obj, NBTTagCompound nbt, String key) {
		if (nbt.hasKey(key)) {
			obj.addProperty(key, nbt.getBoolean(key));
			nbt.remove(key);
		}
	}

	private void copyFloatList(JsonObject obj, NBTTagCompound nbt, String key) {
		if (nbt.hasKey(key)) {
			NBTTagList list = nbt.getList(key, 5);  // 5 = float list
			JsonArray arr = new JsonArray();
			for (int i = 0; i < list.size(); i++) {
				arr.add(list.i(i));
			}
			obj.add(key, arr);
			nbt.remove(key);
		}
	}

	private void copyDoubleList(JsonObject obj, NBTTagCompound nbt, String key) {
		if (nbt.hasKey(key)) {
			NBTTagList list = nbt.getList(key, 6);  // 6 = double list
			JsonArray arr = new JsonArray();
			for (int i = 0; i < list.size(); i++) {
				arr.add(list.h(i));
			}
			obj.add(key, arr);
			nbt.remove(key);
		}
	}

	private void copyCompoundOfDoubles(JsonObject obj, NBTTagCompound nbt, String key) {
		if (nbt.hasKey(key)) {
			NBTTagCompound compound = nbt.getCompound(key);
			JsonObject sobj = new JsonObject();
			for (String comp : compound.getKeys()) {
				sobj.addProperty(comp, compound.getDouble(comp));
			}
			obj.add(key, sobj);
			nbt.remove(key);
		}
	}
}
