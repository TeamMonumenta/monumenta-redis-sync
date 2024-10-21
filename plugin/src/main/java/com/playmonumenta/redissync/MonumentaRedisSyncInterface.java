package com.playmonumenta.redissync;

public interface MonumentaRedisSyncInterface {
	/**
	 * Starts code on an sync thread immediately using the preferred method of the current server
	 * @param runnable The code to run asynchronously
	 */
	void runAsync(Runnable runnable);
}
