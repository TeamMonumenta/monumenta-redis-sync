package com.playmonumenta.redissync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.lettuce.core.Range;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

public class AccountTransferAPI {
	/**
	 * Get a list of all account transfers for any accounts since the given time.
	 * Does not merge intermediate transfers.
	 * @param startTime The time to include for the first transfer
	 * @return A future list of account transfers
	 */
	public static CompletableFuture<List<AccountTransferDetails>> getAllTransfersSince(LocalDateTime startTime) {
		CompletableFuture<List<AccountTransferDetails>> future = new CompletableFuture<>();
		long timestampMillis = AccountTransferManager.EPOCH.until(startTime, ChronoUnit.MILLIS);

		MonumentaRedisSync plugin = MonumentaRedisSync.getInstance();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {

				// Fetch the list of transfers from Redis
				List<String> transferJsonStrList = RedisAPI.getInstance().async().zrangebyscore(AccountTransferManager.REDIS_KEY, Range.from(
					Range.Boundary.including(timestampMillis),
					Range.Boundary.unbounded()
				)).toCompletableFuture().join();

				// Parse them as-is
				Gson gson = new Gson();
				List<AccountTransferDetails> transfers = new ArrayList<>();
				for (String transferJsonStr : transferJsonStrList) {
					JsonObject transferJson = gson.fromJson(transferJsonStr, JsonObject.class);
					transfers.add(new AccountTransferDetails(transferJson));
				}

				// Return the results (should already be sorted)
				future.complete(transfers);
			} catch (Throwable throwable) {
				future.completeExceptionally(throwable);
			}
		});

		return future;
	}

	/**
	 * Get a list of all account transfers for any accounts since the given time.
	 * Merges intermediate transfers to get the effective start and end accounts
	 * @param startTime The time to include for the first transfer
	 * @return A future list of account transfers
	 */
	public static CompletableFuture<List<AccountTransferDetails>> getEffectiveTransfersSince(LocalDateTime startTime) {
		CompletableFuture<List<AccountTransferDetails>> future = new CompletableFuture<>();
		long timestampMillis = AccountTransferManager.EPOCH.until(startTime, ChronoUnit.MILLIS);

		MonumentaRedisSync plugin = MonumentaRedisSync.getInstance();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				// Fetch the list of transfers from Redis
				List<String> transferJsonStrList = RedisAPI.getInstance().async().zrangebyscore(AccountTransferManager.REDIS_KEY, Range.from(
					Range.Boundary.including(timestampMillis),
					Range.Boundary.unbounded()
				)).toCompletableFuture().join();

				// Parse them, merging multiple transfers if needed
				Gson gson = new Gson();
				Map<UUID, AccountTransferDetails> mTransfersByNewId = new HashMap<>();
				for (String transferJsonStr : transferJsonStrList) {
					JsonObject transferJson = gson.fromJson(transferJsonStr, JsonObject.class);
					AccountTransferDetails transferDetails = new AccountTransferDetails(transferJson);

					AccountTransferDetails oldTransfer = mTransfersByNewId.remove(transferDetails.oldId());
					if (oldTransfer == null) {
						mTransfersByNewId.put(transferDetails.newId(), transferDetails);
					} else {
						AccountTransferDetails mergedTransfer = new AccountTransferDetails(oldTransfer, transferDetails);
						mTransfersByNewId.put(mergedTransfer.newId(), mergedTransfer);
					}
				}

				// Sort and return the results
				future.complete(new ArrayList<>(mTransfersByNewId.values().stream()
					.filter(transfer -> !transfer.oldId().equals(transfer.newId()))
					.sorted().toList()));
			} catch (Throwable throwable) {
				future.completeExceptionally(throwable);
			}
		});

		return future;
	}
}
