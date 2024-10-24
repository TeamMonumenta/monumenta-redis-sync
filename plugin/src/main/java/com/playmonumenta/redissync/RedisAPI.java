package com.playmonumenta.redissync;

import com.google.common.util.concurrent.Uninterruptibles;
import com.playmonumenta.networkrelay.util.MMLog;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class RedisAPI {
	private static final class StringByteCodec implements RedisCodec<String, byte[]> {
		private static final StringByteCodec INSTANCE = new StringByteCodec();
		private static final byte[] EMPTY = new byte[0];
		private final Charset mCharset = StandardCharsets.UTF_8;

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

	public static final RedisCodec<String, String> STRING_STRING_CODEC = StringCodec.UTF8;
	public static final RedisCodec<String, byte[]> STRING_BYTE_CODEC = StringByteCodec.INSTANCE;

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

	/**
	 * Opens a new autoClosable connection regardless of open connections
	 * Your code is responsible for closing this when it is done, ideally using a try with resources block
	 * @return A new connection that you are responsible for closing
	 */
	public <K, V> StatefulRedisConnection<K, V> openConnection(RedisCodec<K, V> codec) {
		Thread thread = Thread.currentThread();
		MMLog.info("Creating a new autocloseable connection on thread " + thread.getId());
		return mRedisClient.connect(codec);
	}

	/**
	 * Provides a connection that closes automagically when the executing thread terminates.
	 * If the current thread already has an open connection, that is returned instead.
	 * The main thread may be used as well, and is closed when the plugin is disabled.
	 * @return A connection associated with the current thread
	 */
	@Deprecated
	public StatefulRedisConnection<String, String> getMagicallyClosingStringStringConnection() {
		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		MMLog.info("Magically closing connection request from thread " + thread.getId());
		return mThreadStringStringConnections.computeIfAbsent(threadId, k -> {
			StatefulRedisConnection<String, String> connection = mRedisClient.connect();
			MMLog.info("Created new magically closing connection request from thread " + thread.getId());
			mServer.runAsync(() -> {
				Uninterruptibles.joinUninterruptibly(thread);
				mThreadStringStringConnections.remove(k, connection);
				connection.close();
				MMLog.info("Closed magically closing connection request from thread " + thread.getId());
			});
			return connection;
		});
	}

	/**
	 * Provides a connection that closes automagically when the executing thread terminates.
	 * If the current thread already has an open connection, that is returned instead.
	 * The main thread may be used as well, and is closed when the plugin is disabled.
	 * @return A connection associated with the current thread
	 */
	@Deprecated
	public StatefulRedisConnection<String, byte[]> getMagicallyClosingStringByteConnection() {
		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		return mThreadStringByteConnections.computeIfAbsent(threadId, k -> {
			StatefulRedisConnection<String, byte[]> connection = mRedisClient.connect(STRING_BYTE_CODEC);
			mServer.runAsync(() -> {
				Uninterruptibles.joinUninterruptibly(thread);
				mThreadStringByteConnections.remove(k, connection);
				connection.close();
			});
			return connection;
		});
	}

	@Deprecated
	public RedisCommands<String, String> sync() {
		return mConnection.sync();
	}

	public RedisAsyncCommands<String, String> async() {
		return mConnection.async();
	}

	@Deprecated
	public RedisCommands<String, byte[]> syncStringBytes() {
		return mStringByteConnection.sync();
	}

	public RedisAsyncCommands<String, byte[]> asyncStringBytes() {
		return mStringByteConnection.async();
	}

	public boolean isReady() {
		return mConnection.isOpen() && mStringByteConnection.isOpen();
	}
}
