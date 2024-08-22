package com.playmonumenta.redissync;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.lettuce.core.support.ConnectionPoolSupport;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

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

	private final RedisClient mRedisClient;
	private final GenericObjectPool<StatefulRedisConnection<String, String>> mConnectionPool;
	private final StatefulRedisConnection<String, String> mConnection;
	private final StatefulRedisConnection<String, byte[]> mStringByteConnection;
	private final StatefulRedisPubSubConnection<String, String> mPubSubConnection;

	protected RedisAPI(String hostname, int port) {
		mRedisClient = RedisClient.create(RedisURI.Builder.redis(hostname, port).build());
		mConnectionPool = ConnectionPoolSupport.createGenericObjectPool(() -> mRedisClient.connect(), new GenericObjectPoolConfig<>());
		mConnection = mRedisClient.connect();
		mStringByteConnection = mRedisClient.connect(StringByteCodec.INSTANCE);
		mPubSubConnection = mRedisClient.connectPubSub();
		INSTANCE = this;
	}

	protected void shutdown() {
		mConnection.close();
		mConnectionPool.close();
		mStringByteConnection.close();
		mPubSubConnection.close();
		mRedisClient.shutdown();
	}

	public static RedisAPI getInstance() {
		return INSTANCE;
	}

	public RedisCommands<String, String> sync() {
		return mConnection.sync();
	}

	public RedisAsyncCommands<String, String> async() {
		return mConnection.async();
	}

	public RedisCommands<String, byte[]> syncStringBytes() {
		return mStringByteConnection.sync();
	}

	public RedisAsyncCommands<String, byte[]> asyncStringBytes() {
		return mStringByteConnection.async();
	}

	public RedisPubSubCommands<String, String> syncPubSub() {
		return mPubSubConnection.sync();
	}

	public RedisPubSubAsyncCommands<String, String> asyncPubSub() {
		return mPubSubConnection.async();
	}

	public StatefulRedisPubSubConnection<String, String> pubSubConnection() {
		return mPubSubConnection;
	}

	public GenericObjectPool<StatefulRedisConnection<String, String>> connectionPool() {
		return mConnectionPool;
	}

	public StatefulRedisConnection<String, String> getConnectionFromPool() {
		try {
			return RedisAPI.getInstance().connectionPool().borrowObject();
		} catch (Exception e) {
			// Thanks, apache pools
			throw new RuntimeException("Should never happen; borrowing from connection pool failed.");
		}
	}

	public boolean isReady() {
		return mConnection.isOpen() && mStringByteConnection.isOpen();
	}
}
