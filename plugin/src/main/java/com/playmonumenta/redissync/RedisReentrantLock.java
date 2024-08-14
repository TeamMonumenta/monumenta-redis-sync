package com.playmonumenta.redissync;

import io.lettuce.core.SetArgs;
import io.lettuce.core.TransactionResult;
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
 * <pre>
 * {@code
 * RedisLock lock = new Lock(...);
 * lock.lock();
 * // Critical section code
 * lock.unlock();
 * }
 * </pre>
 *
 * <p>It is recommended to always place the critical section in a try...finally
 * block, to ensure that the lock is always released.</p>
 *
 * <p>This lock uses redis for backing. A provided lock name is used
 * to find a corresponding entry in redis on which to block against
 * if present. To provide crash resilience, this entry has an expiry time,
 * which is refreshed while this shard is alive.</p>
 *
 * <p>Credit: Some parts copied from {@link java.util.concurrent.locks.Lock} javadoc.</p>
 */

// TODO: Double lock behavior
public final class RedisReentrantLock {

	private static final int DEFAULT_TIMEOUT_MS = 10000;
	private static final int DEFAULT_REFRESH_INTERVAL_MS = 100;

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
		Optional<Thread> lockOwner,
		int counter,
		Optional<ScheduledFuture<?>> refreshTask
	) {
		public MutableLockData withLockOwner(Optional<Thread> newLockOwner) {
			return new MutableLockData(
				newLockOwner,
				counter,
				refreshTask
			);
		}

		public MutableLockData withRefreshTask(Optional<ScheduledFuture<?>> newRefreshTask) {
			return new MutableLockData(
				lockOwner,
				counter,
				newRefreshTask
			);
		}

		public MutableLockData increment() {
			return new MutableLockData(
				lockOwner,
				counter + 1,
				refreshTask
			);
		}

		public MutableLockData decrement() {
			return new MutableLockData(
				lockOwner,
				Math.max(0, counter - 1),
				refreshTask
			);
		}

		public MutableLockData resetCounter() {
			return new MutableLockData(
				lockOwner,
				0,
				refreshTask
			);
		}
	}

	public RedisReentrantLock(String lockName) {
		this(lockName, DEFAULT_TIMEOUT_MS, DEFAULT_REFRESH_INTERVAL_MS);
	}

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
	 * Although painful, the internal lock should be held
	 * before calling this method, so that a subscriber
	 * trigger can't happen after this check but before await.
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
	 *
	 * @throws RejectedExecutionException If the inter-shard lock
	 * refresh task fails to be scheduled; upon throw, neither lock
	 * will be held.
	 */
	public void lock() {
		mSynchronizers.internalAccessLock().lock();
		try {
			MutableLockData data = namesToMutableLockData.get(mKeyName);

			// Increment if is owner
			if (data.lockOwner().isPresent() && data.lockOwner().get().equals(Thread.currentThread())) {
				namesToMutableLockData.put(mKeyName, data.increment());
				return;
			}

			// Acquire the intra-shard lock
			while (data.lockOwner().isPresent()) {
				mSynchronizers.isIntraLockedCondition().awaitUninterruptibly();
			}
			data = data.withLockOwner(Optional.of(Thread.currentThread()));
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
						// Create a new thread so separate refresh tasks
						// don't get stuck behind each other.
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
							RedisAPI.getInstance().async().watch(mKeyName);
							Optional<String> lockingShard = Optional.ofNullable(
								RedisAPI.getInstance().sync().get(mKeyName)
							);
							if (lockingShard.isEmpty() || !lockingShard.get().equals(ConfigAPI.getShardName())) {
								RedisAPI.getInstance().async().unwatch();
								return;
							}
							RedisAPI.getInstance().async().multi();
							RedisAPI.getInstance().async().pexpire(mKeyName, mImmutableLockData.timeoutMS());
							RedisAPI.getInstance().async().exec();
						});
					},
					mImmutableLockData.refreshIntervalMS(),
					mImmutableLockData.refreshIntervalMS(),
					TimeUnit.MILLISECONDS
				)
			);
			data = data.withRefreshTask(refreshTask);
			namesToMutableLockData.put(mKeyName, data);
		} catch (RejectedExecutionException e) {
			mSynchronizers.internalAccessLock().unlock();

			// Attempt to release the inter-shard lock
			RedisAPI.getInstance().async().watch(mKeyName);
			Optional<String> lockingShard = Optional.ofNullable(
				RedisAPI.getInstance().sync().get(mKeyName)
			);

			if (lockingShard.isEmpty() || !lockingShard.get().equals(ConfigAPI.getShardName())) {
				RedisAPI.getInstance().async().unwatch();
			} else {
				RedisAPI.getInstance().async().multi();
				RedisAPI.getInstance().async().del(mKeyName);
				RedisAPI.getInstance().async().exec();
			}

			mSynchronizers.internalAccessLock().lock();
			MutableLockData data = namesToMutableLockData.get(mKeyName);
			data.refreshTask().ifPresent(future -> future.cancel(false));
			data = data.withRefreshTask(Optional.empty());
			data = data.withLockOwner(Optional.empty());
			data = data.resetCounter();
			namesToMutableLockData.put(mKeyName, data);
			mSynchronizers.isIntraLockedCondition().signal();
			throw e;
		} finally {
			mSynchronizers.internalAccessLock().unlock();
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
	public void unlock() {
		mSynchronizers.internalAccessLock().lock();
		try {
			// Test for correct thread
			MutableLockData data = namesToMutableLockData.get(mKeyName);
			if (data.lockOwner().isEmpty() || !data.lockOwner().get().equals(Thread.currentThread())) {
				throw new RedisLockException("Attempted to unlock a redis lock not owned by this thread.");
			}

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
		try {
			RedisAPI.getInstance().async().watch(mKeyName);
			Optional<String> lockingShard = Optional.ofNullable(
				RedisAPI.getInstance().sync().get(mKeyName)
			);

			if (lockingShard.isEmpty() || !lockingShard.get().equals(ConfigAPI.getShardName())) {
				RedisAPI.getInstance().async().unwatch();
				throw new RedisLockException("Attempted to unlock a redis lock not owned by this shard; nearly certainly due to expiration during the critical section.");
			}

			RedisAPI.getInstance().async().multi();
			RedisAPI.getInstance().async().del(mKeyName);
			TransactionResult result = RedisAPI.getInstance().sync().exec();
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
				data = data.withLockOwner(Optional.empty());
				data = data.resetCounter();
				namesToMutableLockData.put(mKeyName, data);
				mSynchronizers.isIntraLockedCondition().signal();
			} finally {
				mSynchronizers.internalAccessLock().unlock();
			}
		}
	}

	public static void shutdownExecutors() {
		subscribeSignallers.shutdown();
		refreshScheduler.shutdown();
		refreshWorkers.shutdown();
	}
}
