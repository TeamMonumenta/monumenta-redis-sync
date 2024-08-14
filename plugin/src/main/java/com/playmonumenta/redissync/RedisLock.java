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

	private static final ExecutorService SUBSCRIBE_SIGNALLERS = Executors.newCachedThreadPool();

	private static final ScheduledExecutorService REFRESH_SCHEDULER = Executors.newSingleThreadScheduledExecutor();
	private static final ExecutorService REFRESH_WORKERS = Executors.newCachedThreadPool();
	private final int mRefreshIntervalMS;
	private volatile Optional<ScheduledFuture<?>> mRefreshTask;

	private final Lock mInternalAccessLock;

	private final Condition mIsIntraLockedCondition;
	private final Condition mIsInterLockedCondition;

	private volatile Optional<Thread> mLockOwner;

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

		mRefreshIntervalMS = refreshIntervalMS;
		mRefreshTask = Optional.empty();

		mInternalAccessLock = new ReentrantLock();
		mIsIntraLockedCondition = mInternalAccessLock.newCondition();
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
			SUBSCRIBE_SIGNALLERS.execute(() -> {
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
		// TODO: Investigate unlocking then relocking on future completion
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
	 * Runs watch before getting the shard.
	 *
	 * @return The entry in redis at the key
	 * corresponding to this lock, or None if it
	 * does not exist.
	 */
	private Optional<String> watchThenGetLockingShard() {
		RedisAPI.getInstance()
			.async()
			.watch(mKeyName);
		return Optional.ofNullable(
			RedisAPI.getInstance()
				.sync()
				.get(mKeyName)
		);
	}

	public class RedisLockException extends RuntimeException {
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
			while (mLockOwner.isPresent()) {
				mIsIntraLockedCondition.awaitUninterruptibly();
			}
			mLockOwner = Optional.of(Thread.currentThread());

			// Block until inter-shard lock is free
			RedisAPI.getInstance().asyncPubSub().subscribe("__keyspace@0__:" + mKeyName);
			while (!tryAcquireRedis()) {
				mIsInterLockedCondition.awaitUninterruptibly();
			}
			RedisAPI.getInstance().asyncPubSub().unsubscribe("__keyspace@0__:" + mKeyName);

			// TODO: try catch RejectedExecutionException; gracefully unwind
			mRefreshTask = Optional.of(
				REFRESH_SCHEDULER.scheduleAtFixedRate(
					() -> {
						// Create a new thread so separate refresh tasks
						// don't get stuck behind each other.
						REFRESH_WORKERS.submit(() -> {
							// Mind the case: key is deleted before this task is shut down,
							// this task's last run would refresh a different shard's lock.

							// The lifetime of this refresh task could outlive a critical section

							/**
							 * Two outcomes:
							 *
							 * Found self:
							 * WATCH
							 * self_shard = GET mKeyName
							 * MULTI
							 * PEXPIRE
							 * EXEC
							 * - You know that if the key is self
							 * - then changes, it must be non-self;
							 * - do not refresh nonself.
							 *
							 * Found else:
							 * WATCH
							 * other_shard = GET mKeyName
							 * UNWATCH
							 * - If the other shard changes to you,
							 * - That means you have acquired the lock
							 * - A separate refresh task has started.
							 */
							Optional<String> lockingShard = watchThenGetLockingShard();
							if (lockingShard.isEmpty() || !lockingShard.get().equals(ConfigAPI.getShardName())) {
								RedisAPI.getInstance()
									.async()
									.unwatch();
								return;
							}
							RedisAPI.getInstance()
								.async()
								.multi();
							RedisAPI.getInstance()
								.async()
								.pexpire(mKeyName, mTimeoutMS);
							RedisAPI.getInstance()
								.async()
								.exec();
						});
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

			try {
				Optional<String> lockingShard = watchThenGetLockingShard();
				if (lockingShard.isEmpty() || !lockingShard.get().equals(ConfigAPI.getShardName())) {
					RedisAPI.getInstance()
						.async()
						.unwatch();
					throw new RedisLockException("Attempted to unlock a redis lock not owned by this shard; nearly certainly due to expiration during the critical section.");
				}

				RedisAPI.getInstance()
					.async()
					.multi();
				RedisAPI.getInstance()
					.async()
					.del(mKeyName);
				if (
					RedisAPI.getInstance()
						.sync()
						.exec()
						.wasDiscarded()
				) {
					throw new RedisLockException("Attempted to unlock a redis lock not owned by this shard; nearly certainly due to expiration mid-unlock.");
				}
			} finally {
				mRefreshTask.ifPresent(future -> future.cancel(false));
				mRefreshTask = Optional.empty();
				mLockOwner = Optional.empty();
				mIsIntraLockedCondition.signal();
			}
		} finally {
			mInternalAccessLock.unlock();
		}
	}

	public static void shutdownExecutors() {
		SUBSCRIBE_SIGNALLERS.shutdown();
		REFRESH_SCHEDULER.shutdown();
		REFRESH_WORKERS.shutdown();
	}
}
