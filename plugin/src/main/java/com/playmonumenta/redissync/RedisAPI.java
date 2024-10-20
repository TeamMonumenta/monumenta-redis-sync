package com.playmonumenta.redissync;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.RedisCodec;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;

public class RedisAPI {
	private static final class StringByteCodec implements RedisCodec<String, byte[]> {
		private static final StringByteCodec INSTANCE = new StringByteCodec();
		private static final byte[] EMPTY = new byte[0];
		private final Charset mCharset = Charset.forName("UTF-8");

		@Override
		public String decodeKey(final ByteBuffer bytes) {
			return mCharset.decode(bytes).toString();
		}

		@Override
		public byte[] decodeValue(final ByteBuffer bytes) {
			return getBytes(bytes);
		}

		@Override
		public ByteBuffer encodeKey(final String key) {
			return mCharset.encode(key);
		}

		@Override
		public ByteBuffer encodeValue(final byte[] value) {
			if (value == null) {
				return ByteBuffer.wrap(EMPTY);
			}

			return ByteBuffer.wrap(value);
		}

		private static byte[] getBytes(final ByteBuffer buffer) {
			final byte[] b = new byte[buffer.remaining()];
			buffer.get(b);
			return b;
		}
	}

	@SuppressWarnings("NullAway") // Required to avoid many null checks, this class will always be instantiated if this plugin is loaded
	private static RedisAPI INSTANCE = null;

	private final MonumentaRedisSyncInterface mServer;
	private final RedisClient mRedisClient;
	private final StatefulRedisConnection<String, String> mConnection;
	private final StatefulRedisConnection<String, byte[]> mStringByteConnection;
	private final ConcurrentHashMap<Long, StatefulRedisConnection<String, String>> mThreadStringStringConnections
		= new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, StatefulRedisConnection<String, byte[]>> mThreadStringByteConnections
		= new ConcurrentHashMap<>();

	protected RedisAPI(MonumentaRedisSyncInterface server, String hostname, int port) {
		mServer = server;
		mRedisClient = RedisClient.create(RedisURI.Builder.redis(hostname, port).build());
		mConnection = mRedisClient.connect();
		mStringByteConnection = mRedisClient.connect(StringByteCodec.INSTANCE);

		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		mThreadStringStringConnections.put(threadId, mConnection);
		mThreadStringByteConnections.put(threadId, mStringByteConnection);

		INSTANCE = this;
	}

	protected void shutdown() {
		mConnection.close();
		mStringByteConnection.close();
		mRedisClient.shutdown();
	}

	public static RedisAPI getInstance() {
		return INSTANCE;
	}

	public RedisCommands<String, String> sync() {
		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		//noinspection resource
		return mThreadStringStringConnections.computeIfAbsent(threadId, k -> {
			StatefulRedisConnection<String, String> connection = mRedisClient.connect();
			mServer.runAsync(() -> {
				while (true) {
					try {
						thread.join();
						break;
					} catch (InterruptedException ignored) {
						// We don't care about interrupts in the current thread;
						// We're just waiting for the other thread to be done.
						// The fact the exception was thrown means the interrupt status was cleared
					}
				}
				mThreadStringStringConnections.remove(k, connection);
				connection.close();
			});
			return connection;
		}).sync();
	}

	public RedisAsyncCommands<String, String> async() {
		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		//noinspection resource
		return mThreadStringStringConnections.computeIfAbsent(threadId, k -> {
			StatefulRedisConnection<String, String> connection = mRedisClient.connect();
			mServer.runAsync(() -> {
				while (true) {
					try {
						thread.join();
						break;
					} catch (InterruptedException ignored) {
						// We don't care about interrupts in the current thread;
						// We're just waiting for the other thread to be done.
						// The fact the exception was thrown means the interrupt status was cleared
					}
				}
				mThreadStringStringConnections.remove(k, connection);
				connection.close();
			});
			return connection;
		}).async();
	}

	public RedisCommands<String, byte[]> syncStringBytes() {
		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		//noinspection resource
		return mThreadStringByteConnections.computeIfAbsent(threadId, k -> {
			StatefulRedisConnection<String, byte[]> connection = mRedisClient.connect(StringByteCodec.INSTANCE);
			mServer.runAsync(() -> {
				while (true) {
					try {
						thread.join();
						break;
					} catch (InterruptedException ignored) {
						// We don't care about interrupts in the current thread;
						// We're just waiting for the other thread to be done.
						// The fact the exception was thrown means the interrupt status was cleared
					}
				}
				mThreadStringStringConnections.remove(k, connection);
				connection.close();
			});
			return connection;
		}).sync();
	}

	public RedisAsyncCommands<String, byte[]> asyncStringBytes() {
		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		//noinspection resource
		return mThreadStringByteConnections.computeIfAbsent(threadId, k -> {
			StatefulRedisConnection<String, byte[]> connection = mRedisClient.connect(StringByteCodec.INSTANCE);
			mServer.runAsync(() -> {
				while (true) {
					try {
						thread.join();
						break;
					} catch (InterruptedException ignored) {
						// We don't care about interrupts in the current thread;
						// We're just waiting for the other thread to be done.
						// The fact the exception was thrown means the interrupt status was cleared
					}
				}
				mThreadStringStringConnections.remove(k, connection);
				connection.close();
			});
			return connection;
		}).async();
	}

	public boolean isReady() {
		return mConnection.isOpen() && mStringByteConnection.isOpen();
	}
}
