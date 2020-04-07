package com.playmonumenta.redissync.adapters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_13_R2.CraftServer;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.server.v1_13_R2.EntityPlayer;
import net.minecraft.server.v1_13_R2.NBTCompressedStreamTools;
import net.minecraft.server.v1_13_R2.NBTTagCompound;
import net.minecraft.server.v1_13_R2.NBTTagDouble;
import net.minecraft.server.v1_13_R2.NBTTagFloat;
import net.minecraft.server.v1_13_R2.NBTTagList;
import net.minecraft.server.v1_13_R2.PlayerList;

public class VersionAdapter113 implements VersionAdapter {
	private Gson mGson = new Gson();

	public Object retrieveSaveData(Player player, byte[] data, String shardData) throws IOException {

		ByteArrayInputStream inBytes = new ByteArrayInputStream(data);
		NBTTagCompound nbt = NBTCompressedStreamTools.a(new Base64InputStream(inBytes));

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

	public SaveData extractSaveData(Player player, Object nbtObj) throws IOException {
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

		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
		NBTCompressedStreamTools.a(nbt, new Base64OutputStream(outBytes));
		return new SaveData(outBytes.toByteArray(), obj.toString());
	}

	public void savePlayer(Player player) throws Exception {
		PlayerList playerList = ((CraftServer)Bukkit.getServer()).getHandle();

		Method method = PlayerList.class.getDeclaredMethod("savePlayerFile", EntityPlayer.class);
		method.setAccessible(true);
		method.invoke(playerList, ((CraftPlayer)player).getHandle());
	}

	protected NBTTagList toDoubleList(double... doubles) {
        NBTTagList nbttaglist = new NBTTagList();

		for (double d : doubles) {
            nbttaglist.add(new NBTTagDouble(d));
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
					nbttaglist.add(new NBTTagFloat(val.getAsFloat()));
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
					nbttaglist.add(new NBTTagDouble(val.getAsDouble()));
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
				arr.add(list.k(i));
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
				arr.add(list.k(i));
			}
			obj.add(key, arr);
			nbt.remove(key);
		}
	}
}
