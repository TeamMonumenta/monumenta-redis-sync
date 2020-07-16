package com.playmonumenta.redissync.adapters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_15_R1.CraftServer;
import org.bukkit.craftbukkit.v1_15_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.Dynamic;
import com.mojang.datafixers.types.JsonOps;
import com.playmonumenta.redissync.DataEventListener.ReturnParams;

import net.minecraft.server.v1_15_R1.AdvancementDataPlayer;
import net.minecraft.server.v1_15_R1.DataFixTypes;
import net.minecraft.server.v1_15_R1.EntityPlayer;
import net.minecraft.server.v1_15_R1.GameProfileSerializer;
import net.minecraft.server.v1_15_R1.NBTCompressedStreamTools;
import net.minecraft.server.v1_15_R1.NBTTagCompound;
import net.minecraft.server.v1_15_R1.NBTTagDouble;
import net.minecraft.server.v1_15_R1.NBTTagFloat;
import net.minecraft.server.v1_15_R1.NBTTagList;
import net.minecraft.server.v1_15_R1.PlayerList;
import net.minecraft.server.v1_15_R1.SharedConstants;

public class VersionAdapter115 implements VersionAdapter {
	private Gson mGson = new Gson();
	private Method mSaveMethod = null;
	private static Gson advancementsGson = null;

	public Object retrieveSaveData(byte[] data, String shardData) throws IOException {

		ByteArrayInputStream inBytes = new ByteArrayInputStream(data);
		NBTTagCompound nbt = NBTCompressedStreamTools.a(inBytes);

		if (shardData == null) {
			/* If player has never been to this shard, put them at world spawn */
			Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
			nbt.set("Pos", toDoubleList(spawn.getX(), spawn.getY(), spawn.getZ()));
		} else {
			JsonObject obj = mGson.fromJson(shardData, JsonObject.class);
			applyDoubleList(obj, nbt, "Pos");
			applyInt(obj, nbt, "SpawnX");
			applyInt(obj, nbt, "SpawnY");
			applyInt(obj, nbt, "SpawnZ");
			applyBool(obj, nbt, "SpawnForced");
			applyStr(obj, nbt, "SpawnWorld");
			applyBool(obj, nbt, "FallFlying");
			applyFloat(obj, nbt, "FallDistance");
			applyBool(obj, nbt, "OnGround");
			applyInt(obj, nbt, "Dimension");
			applyDoubleList(obj, nbt, "Pos");
			applyDoubleList(obj, nbt, "Motion");
			applyFloatList(obj, nbt, "Rotation");
		}

		return nbt;
	}

	public SaveData extractSaveData(Object nbtObj, ReturnParams returnParams) throws IOException {
		NBTTagCompound nbt = (NBTTagCompound) nbtObj;

		JsonObject obj = new JsonObject();
		copyInt(obj, nbt, "SpawnX");
		copyInt(obj, nbt, "SpawnY");
		copyInt(obj, nbt, "SpawnZ");
		copyBool(obj, nbt, "SpawnForced");
		copyStr(obj, nbt, "SpawnWorld");
		copyBool(obj, nbt, "FallFlying");
		copyFloat(obj, nbt, "FallDistance");
		copyBool(obj, nbt, "OnGround");
		copyInt(obj, nbt, "Dimension");
		copyDoubleList(obj, nbt, "Pos");
		copyDoubleList(obj, nbt, "Motion");
		copyFloatList(obj, nbt, "Rotation");

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
}