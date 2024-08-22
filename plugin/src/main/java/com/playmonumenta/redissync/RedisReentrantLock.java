package com.playmonumenta.redissync;

import io.lettuce.core.SetArgs;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubListener;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An lock is a tool for controlling access to a shared resource
 * by multiple accessors. In this RedisLock's case, these accessors
 * are threads on separate monumenta-redis-sync shards.
 *
 * <p>Only one thread among all shards can acquire the lock at a time.
 * Therefore, if all shared resource accesses require having acquired
 * the lock, then these accesses are guaranteed to be exclusive
 * to the thread holding the lock. In particular, this is useful for
 * ensuring the shared resource stays invariant, thereby preventing
 * some classes of race conditions.</p>
 *
 * <p>The code executed between acquiring and releasing the lock is called
 * the <bold>critical section</bold>.</p>
 *
 * <pre>{@code
 * RedisLock lock = new Lock(...);
 * lock.lock();
 * // Critical section code
 * lock.unlock();}</pre>
 *
 * <p>It is recommended to always place the critical section in a try...finally
 * block, to ensure that the lock is always released:</p>
 *
 * <pre>{@code
 * RedisLock lock = new Lock(...);
 * lock.lock();
 * try {
 *     // Critical section code
 * } finally {
 *     lock.unlock();
 * }}</pre>
 *
 * <p>This lock is a reentrant lock, so the same thread may acquire the lock
 * multiple times. Each lock increments an internal counter, and
 * each unlock decrements that counter; when the counter hits 0,
 * the lock will release.</p>
 *
 * <p>This lock uses redis for backing. A provided lock name is used
 * to find a corresponding entry in redis on which to block against
 * if present. To provide crash resilience, this redis entry has an expiry time,
 * which is refreshed on a separate thread during the period of the critical section.
 * Beware that sufficiently short expiry times or expiry times less than refresh
 * interval times may cause expiry mid-critical section, and can cause
 * loss of exclusive access; in this case, if an unlock sees another, it will throw a {@code RedisLockException}</p>
 *
 * <p>Implementation detail: Note also that intra-shard synchronization is on a per-name basis,
 * rather than blocking on an instance of a {@code RedisReentrantLock} object.</p>
 *
 * <p>Credit: Some parts copied from {@link java.util.concurrent.locks.Lock} javadoc.</p>
 */
public final class RedisReentrantLock {

	public static final int DEFAULT_TIMEOUT_MS = 10000;
	public static final int DEFAULT_REFRESH_INTERVAL_MS = 100;

	private static final ExecutorService subscribeSignallers = Executors.newCachedThreadPool();
	private static final ScheduledExecutorService refreshScheduler = Executors.newSingleThreadScheduledExecutor();
	private static final ExecutorService refreshWorkers = Executors.newCachedThreadPool();

	// Invariant: once an entry is set in this map, it is immutable.
	private static final ConcurrentHashMap<String, Synchronizers> namesToSynchronizationConstructs = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, ImmutableLockData> namesToImmutableLockData = new ConcurrentHashMap<>();

	// This map does not have any such invariant, and access should
	// be guarded by the above.
	private static final ConcurrentHashMap<String, MutableLockData> namesToMutableLockData = new ConcurrentHashMap<>();

	private final String mKeyName;
	private final Synchronizers mSynchronizers;
	private final ImmutableLockData mImmutableLockData;

	private record Synchronizers(
		ReentrantLock internalAccessLock,
		Condition isIntraLockedCondition,
		Condition isInterLockedCondition
	) {}

	private record ImmutableLockData(
		int timeoutMS,
		int refreshIntervalMS
	) {}

	private record MutableLockData(
		Optional<Thread> lockingThread,
		int counter,
		Optional<ScheduledFuture<?>> refreshTask
	) {
		public MutableLockData withLockingThread(Optional<Thread> newLockingThread) {
			return new MutableLockData(
				newLockingThread,
				counter,
				refreshTask
			);
		}

		public MutableLockData withRefreshTask(Optional<ScheduledFuture<?>> newRefreshTask) {
			return new MutableLockData(
				lockingThread,
				counter,
				newRefreshTask
			);
		}

		public MutableLockData increment() {
			return new MutableLockData(
				lockingThread,
				counter + 1,
				refreshTask
			);
		}

		public MutableLockData decrement() {
			return new MutableLockData(
				lockingThread,
				Math.max(0, counter - 1),
				refreshTask
			);
		}

		public MutableLockData resetCounter() {
			return new MutableLockData(
				lockingThread,
				0,
				refreshTask
			);
		}
	}

	/**
	 * Constructs a RedisReentrantLock with the given name.
	 *
	 * <p>Also sets the global redis expiry time and global
	 * refresh task interval corresponding to this lock name
	 * to their defaults, provided they haven't already been
	 * set. These defaults are exposed as public constants
	 * under {@code DEFAULT_TIMEOUT_MS} and
	 * {@code DEFAULT_REFRESH_INTERVAL_MS}.</p>
	 *
	 * @param lockName the name used to construct the backing redis
	 * key name for this lock. The full key name will be formatted
	 * {@code ConfigAPI.getServerDomain() + ":locks:" + lockName}
	 * and appears in redis as a string key.
	 */
	public RedisReentrantLock(String lockName) {
		this(lockName, DEFAULT_TIMEOUT_MS, DEFAULT_REFRESH_INTERVAL_MS);
	}

	/**
	 * Constructs a RedisReentrantLock with the given name.
	 *
	 * <p>Also sets the global redis expiry time and global
	 * refresh task interval corresponding to this lock name
	 * to those given, provided they haven't already been
	 * set.</p>
	 *
	 * @param lockName the name used to construct the backing redis
	 * key name for this lock. The full key name will be formatted
	 * {@code ConfigAPI.getServerDomain() + ":locks:" + lockName}
	 * and appears in redis as a string key.
	 * @param timeoutMS The expiry time for the key in redis, in milliseconds.
	 * @param refreshIntervalMS The time interval at which a separate thread will
	 * refresh the expiry time for the key in redis, in milliseconds.
	 */
	public RedisReentrantLock(String lockName, int timeoutMS, int refreshIntervalMS) {
		mKeyName = ConfigAPI.getServerDomain() + ":locks:" + lockName;

		mSynchronizers = namesToSynchronizationConstructs.computeIfAbsent(mKeyName, key -> {
			ReentrantLock internalAccessLock = new ReentrantLock();
			Synchronizers result = new Synchronizers(
				internalAccessLock,
				internalAccessLock.newCondition(),
				internalAccessLock.newCondition()
			);
			// Side effect feels nasty, but I need atomicity with absence check
			RedisAPI.getInstance()
				.pubSubConnection()
				.addListener(new LockSubscriber(mKeyName, result));
			return result;
		});

		mImmutableLockData = namesToImmutableLockData.computeIfAbsent(mKeyName, key -> new ImmutableLockData(
			timeoutMS,
			refreshIntervalMS
		));

		// Note; if absent, it is guaranteed that nobody is holding the internal access lock
		// to the mutable data; that is because the internal access lock can only be held
		// by any thread that has constructed a redis lock with this key name, which must
		// have already computed this.
		namesToMutableLockData.computeIfAbsent(mKeyName, key -> new MutableLockData(
			Optional.empty(),
			0,
			Optional.empty()
		));

	}

	private static final class LockSubscriber implements RedisPubSubListener<String, String> {

		private final String mKeyName;
		private final Synchronizers mSynchronizers;

		public LockSubscriber(String keyName, Synchronizers synchronizers) {
			mKeyName = keyName;
			mSynchronizers = synchronizers;
		}

		@Override
		public void message(String channel, String message) {
			if (!channel.equals("__keyspace@0__:" + mKeyName)) {
				return;
			}
			subscribeSignallers.execute(() -> {
				/**
				 * Acquiring the lock here prevents signalling after
				 * tryAcquireRedis fails but before condition await;
				 * this prevents the thread from being dead until the next
				 * expiry/deletion
				 */
				mSynchronizers.internalAccessLock().lock();
				switch (message) {
					case "del" -> mSynchronizers.isInterLockedCondition().signal();
					case "expired" -> mSynchronizers.isInterLockedCondition().signal();
					default -> { }
				}
				mSynchronizers.internalAccessLock().unlock();
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
	 *
	 * <p>Although painful, the internal lock should be held
	 * before calling this method, so that a subscriber
	 * trigger can't happen after this check but before await.</p>
	 * @return if acquiring the lock on redis was successful.
	 */
	private boolean tryAcquireRedis() {
		return Optional.ofNullable(
			RedisAPI.getInstance()
				.sync()
				.set(
					mKeyName,
					ConfigAPI.getShardName(),
					SetArgs.Builder.nx().px(mImmutableLockData.timeoutMS())
				)
		)
		.map(response -> "OK".equals(response))
		.orElse(false);
	}

	/**
	 * Thrown by a failing unlock, where this thread or shard
	 * does not own the lock.
	 */
	public class RedisLockException extends RuntimeException {
		public RedisLockException() {
			super();
		}

		public RedisLockException(String message) {
			super(message);
		}
	}

	/**
	 * Attempts to acquire the lock.
	 *
	 * If the lock is held by another thread or another shard,
	 * this thread will block until it can acquire the lock.
	 *
	 * If the lock is not held by another thread or shard,
	 * this thread will acquire the lock immediately.
	 *
	 * If the lock is held by this thread, the internal counter
	 * will be incremented and this method will return immediately.
	 *
	 * @throws RejectedExecutionException If the redis key
	 * refresh task fails to be scheduled; upon throw, no
	 * locks will be held.
	 */
	public void lock() {
		mSynchronizers.internalAccessLock().lock();
		try {
			MutableLockData data = namesToMutableLockData.get(mKeyName);
			// Increment if is owner
			boolean currentThreadOwnsLock =
				data.lockingThread()
					.filter(lockingThread -> lockingThread.equals(Thread.currentThread()))
					.isPresent();
			if (currentThreadOwnsLock) {
				namesToMutableLockData.put(mKeyName, data.increment());
				return;
			}

			// Acquire the intra-shard lock
			while ((data = namesToMutableLockData.get(mKeyName)).lockingThread().isPresent()) {
				mSynchronizers.isIntraLockedCondition().awaitUninterruptibly();
			}
			data = data.withLockingThread(Optional.of(Thread.currentThread()));
			data = data.increment();
			namesToMutableLockData.put(mKeyName, data);

			// Acquire the inter-shard lock
			RedisAPI.getInstance().asyncPubSub().subscribe("__keyspace@0__:" + mKeyName);
			while (!tryAcquireRedis()) {
				mSynchronizers.isInterLockedCondition().awaitUninterruptibly();
			}
			RedisAPI.getInstance().asyncPubSub().unsubscribe("__keyspace@0__:" + mKeyName);

			Optional<ScheduledFuture<?>> refreshTask = Optional.of(
				refreshScheduler.scheduleAtFixedRate(
					() -> {
						/* WARNING: Thrown RejectedExecution REFRESH_WORKERS executions are not logged. */
						// Create a new thread so separate refresh tasks don't get stuck behind each other.
						refreshWorkers.submit(() -> {
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
							try (StatefulRedisConnection<String, String> connection = RedisAPI.getInstance().getConnectionFromPool()) {
								connection.async().watch(mKeyName);
								Optional.ofNullable(
									connection.sync().get(mKeyName)
								)
								.filter(lockingShard -> lockingShard.equals(ConfigAPI.getShardName()))
								.ifPresentOrElse(
									lockingShard -> {
										connection.async().multi();
										connection.async().pexpire(mKeyName, mImmutableLockData.timeoutMS());
										connection.async().exec();
									},
									() -> connection.async().unwatch()
								);
							}
						});
					},
					mImmutableLockData.refreshIntervalMS(),
					mImmutableLockData.refreshIntervalMS(),
					TimeUnit.MILLISECONDS
				)
			);
			data = namesToMutableLockData.get(mKeyName);
			data = data.withRefreshTask(refreshTask);
			namesToMutableLockData.put(mKeyName, data);
		} catch (RejectedExecutionException e) {
			mSynchronizers.internalAccessLock().unlock();

			// Attempt to release the inter-shard lock
			try (StatefulRedisConnection<String, String> connection = RedisAPI.getInstance().getConnectionFromPool()) {
				connection.async().watch(mKeyName);
				Optional.ofNullable(
					connection.sync().get(mKeyName)
				)
				.filter(lockingShard -> lockingShard.equals(ConfigAPI.getShardName()))
				.ifPresentOrElse(
					lockingShard -> {
						connection.async().multi();
						connection.async().del(mKeyName);
						connection.async().exec();
					},
					() -> connection.async().unwatch()
				);
			}

			mSynchronizers.internalAccessLock().lock();
			// Release the intra-shard lock
			MutableLockData data = namesToMutableLockData.get(mKeyName);
			data.refreshTask().ifPresent(future -> future.cancel(false));
			data = data.withRefreshTask(Optional.empty());
			data = data.withLockingThread(Optional.empty());
			data = data.resetCounter();
			namesToMutableLockData.put(mKeyName, data);
			mSynchronizers.isIntraLockedCondition().signal();
			throw e;
		} finally {
			mSynchronizers.internalAccessLock().unlock();
		}
	}

	/**
	 * Decrements the internal counter; if it reaches 0, it
	 * releases the lock, deleting the key entry in redis.
	 *
	 * @throws RedisLockException If this method is called
	 * by a thread that does not own the intra-shard lock or
	 * a shard that does not own the inter-shard lock.
	 */
	public void unlock() {
		mSynchronizers.internalAccessLock().lock();
		try {
			// Test for correct thread
			MutableLockData data = namesToMutableLockData.get(mKeyName);
			data.lockingThread()
				.filter(lockingThread -> lockingThread.equals(Thread.currentThread()))
				.orElseThrow(() -> new RedisLockException("Attempted to unlock a redis lock not owned by this thread."));

			// Decrement if counter greater than 1
			if (data.counter() > 1) {
				data = data.decrement();
				namesToMutableLockData.put(mKeyName, data);
				return;
			}
		} finally {
			mSynchronizers.internalAccessLock().unlock();
		}

		// Attempt to release the inter-shard lock
		try (StatefulRedisConnection<String, String> connection = RedisAPI.getInstance().getConnectionFromPool()) {
			connection.async().watch(mKeyName);
			Optional.ofNullable(
				connection.sync().get(mKeyName)
			)
			.filter(lockingShard -> lockingShard.equals(ConfigAPI.getShardName()))
			.orElseThrow(() -> {
				connection.async().unwatch();
				return new RedisLockException("Attempted to unlock a redis lock not owned by this shard; nearly certainly due to expiration during the critical section.");
			});

			connection.async().multi();
			connection.async().del(mKeyName);
			TransactionResult result = connection.sync().exec();
			if (result.wasDiscarded()) {
				throw new RedisLockException("Attempted to unlock a redis lock not owned by this shard; nearly certainly due to expiration mid-unlock.");
			}
		} finally {
			// Every exception only occurs as a
			// result of this shard not owning the redis key, so we can't
			// do anything about that.
			mSynchronizers.internalAccessLock().lock();
			try {
				// Release intra-shard lock
				MutableLockData data = namesToMutableLockData.get(mKeyName);
				data.refreshTask().ifPresent(future -> future.cancel(false));
				data = data.withRefreshTask(Optional.empty());
				data = data.withLockingThread(Optional.empty());
				data = data.resetCounter();
				namesToMutableLockData.put(mKeyName, data);
				mSynchronizers.isIntraLockedCondition().signal();
			} finally {
				mSynchronizers.internalAccessLock().unlock();
			}
		}
	}

	/**
	 * Shuts down refresh tasks and other internal executors.
	 */
	public static void shutdownExecutors() {
		subscribeSignallers.shutdown();
		refreshScheduler.shutdown();
		refreshWorkers.shutdown();
	}
}
