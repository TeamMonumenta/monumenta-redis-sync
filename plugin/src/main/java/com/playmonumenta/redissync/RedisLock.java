package com.playmonumenta.redissync;

import io.lettuce.core.SetArgs;
import io.lettuce.core.pubsub.RedisPubSubListener;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An advisory locking mechanism for redis.
 */
public class RedisLock {

	private final String mKeyName;
	private final int mTimeoutMS;

	private final ExecutorService mSubscribeSignaler;

	private final ScheduledExecutorService mRefreshScheduler;
	private final int mRefreshIntervalMS;
	private Optional<ScheduledFuture<?>> mRefreshTask;

	private Lock mInternalAccessLock;

	private Condition mIsIntraLockedCondition;
	private boolean mIsIntraLocked;

	private Condition mIsInterLockedCondition;

	private Optional<Thread> mLockOwner;

	/**
	 * Constructs a redis lock.
	 *
	 * @param lockName The key name postfix; acquiring
	 * the inter-shard lock will set the key
	 * {@code ConfigAPI.getServerDomain()}:locks:{@code lockName} in redis
	 * to the name of this shard.
	 * @param timeoutMS The expiry time for the key in redis, in milliseconds.
	 * The expiry time of the key in redis is set to this with PX argument.
	 * @param refreshIntervalMS While the lock is acquired, a separate thread
	 * will refresh the expiry time on the key in redis every refreshIntervalMS
	 * milliseconds.
	 */
	public RedisLock(String lockName, int timeoutMS, int refreshIntervalMS) {
		mKeyName = ConfigAPI.getServerDomain() + ":locks:" + lockName;
		mTimeoutMS = timeoutMS;

		mSubscribeSignaler = Executors.newSingleThreadExecutor();

		mRefreshScheduler = Executors.newSingleThreadScheduledExecutor();
		mRefreshIntervalMS = refreshIntervalMS;
		mRefreshTask = Optional.empty();

		mInternalAccessLock = new ReentrantLock();

		mIsIntraLockedCondition = mInternalAccessLock.newCondition();
		mIsIntraLocked = false;

		mIsInterLockedCondition = mInternalAccessLock.newCondition();

		mLockOwner = Optional.empty();

		RedisAPI.getInstance()
				.pubSubConnection()
				.addListener(new LockSubscriber());
	}

	private class LockSubscriber implements RedisPubSubListener<String, String> {

		@Override
		public void message(String channel, String message) {
			if (!channel.equals("__keyspace@0__:" + mKeyName)) {
				return;
			}
			mSubscribeSignaler.execute(() -> {
				/**
				 * Acquiring the lock here prevents signalling after
				 * tryAcquireRedis fails but before condition await;
				 * this prevents the thread from being dead until the next
				 * expiry/deletion
				 */
				mInternalAccessLock.lock();
				switch (message) {
					case "del" -> mIsInterLockedCondition.signal();
					case "expired" -> mIsInterLockedCondition.signal();
					default -> { }
				}
				mInternalAccessLock.unlock();
			});
		}

		@Override
		public void message(String pattern, String channel, String message) {

		}

		@Override
		public void subscribed(String channel, long count) {

		}

		@Override
		public void psubscribed(String pattern, long count) {

		}

		@Override
		public void unsubscribed(String channel, long count) {

		}

		@Override
		public void punsubscribed(String pattern, long count) {

		}

	}

	/**
	 * Tries to acquire the lock on redis.
	 * @return if acquiring the lock on redis was successful.
	 */
	private boolean tryAcquireRedis() {
		return Optional.ofNullable(
			RedisAPI.getInstance()
				.sync()
				.set(mKeyName, ConfigAPI.getShardName(), SetArgs.Builder.nx().px(mTimeoutMS))
		)
		.map(response -> "OK".equals(response))
		.orElse(false);
	}

	/**
	 * Gets the shard holding the inter-shard lock.
	 * @return The entry in redis at the key
	 * corresponding to this lock, or None if it
	 * does not exist.
	 */
	private Optional<String> getLockingShard() {
		return Optional.ofNullable(
			RedisAPI.getInstance()
				.sync()
				.get(mKeyName)
		);
	}

	public class RedisLockException extends Exception {
		public RedisLockException() {
			super();
		}

		public RedisLockException(String message) {
			super(message);
		}
	}

	/**
	 * Attempts to acquire the lock, and blocks this thread
	 * until it is able to do so.
	 *
	 * Upon return, this thread will own both the intra-shard lock
	 * and the inter-shard lock.
	 */
	public void lock() {
		mInternalAccessLock.lock();
		try {
			// Block until intra-shard lock is free
			while (mIsIntraLocked) {
				mIsIntraLockedCondition.awaitUninterruptibly();
			}
			// Acquire intra-shard lock
			// (Internal access lock only allows one thread at a time
			// to proceed out of the previous while loop)
			mIsIntraLocked = true;
			mLockOwner = Optional.of(Thread.currentThread());

			// Block until inter-shard lock is free
			RedisAPI.getInstance().syncPubSub().subscribe("__keyspace@0__:" + mKeyName);
			while (!tryAcquireRedis()) {
				mIsInterLockedCondition.awaitUninterruptibly();
			}
			RedisAPI.getInstance().syncPubSub().unsubscribe("__keyspace@0__:" + mKeyName);

			mRefreshTask = Optional.of(
				mRefreshScheduler.scheduleAtFixedRate(
					() -> {
						// Mind the case: key is deleted before this task is shut down,
						// this task's last run would refresh a different shard's lock.

						// The lifetime of this refresh task could outlive a critical section
						Optional<String> lockingShard = getLockingShard();
						if (lockingShard.isEmpty() || !lockingShard.get().equals(ConfigAPI.getShardName())) {
							return;
						}
						RedisAPI.getInstance()
							.async()
							.pexpire(mKeyName, mTimeoutMS);
					},
					mRefreshIntervalMS,
					mRefreshIntervalMS,
					TimeUnit.MILLISECONDS
				)
			);
		} finally {
			mInternalAccessLock.unlock();
		}
	}

	/**
	 * Releases the lock, deleting the key entry
	 * in redis.
	 *
	 * @throws RedisLockException If this method is called
	 * by a thread that does not own the intra-shard lock or
	 * a shard that does not own the inter-shard lock.
	 */
	public void unlock() throws RedisLockException {
		mInternalAccessLock.lock();
		try {
			if (mLockOwner.isEmpty() || !mLockOwner.get().equals(Thread.currentThread())) {
				throw new RedisLockException("Attempted to unlock a redis lock not owned by this thread.");
			}
			Optional<String> lockingShard = getLockingShard();
			if (lockingShard.isEmpty() || !lockingShard.get().equals(ConfigAPI.getShardName())) {
				throw new RedisLockException("Attempted to unlock a redis lock not owned by this shard.");
			}
			mRefreshTask.ifPresent(future -> future.cancel(false));
			RedisAPI.getInstance()
				.async()
				.del(mKeyName);
			mIsIntraLocked = false;
			mLockOwner = Optional.empty();
			mIsIntraLockedCondition.signal();
		} finally {
			mInternalAccessLock.unlock();
		}
	}
}
