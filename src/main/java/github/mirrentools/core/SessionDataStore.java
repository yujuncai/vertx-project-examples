package github.mirrentools.core;

import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Shareable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无状态会话数据存储,该会话存储为单例,当初始化后timeout就固定create无效
 *
 * YU
 * @version 创建时间：2019年8月26日 下午4:16:10
 *
 */
public class SessionDataStore {
	/**
	 * 创建一个会话数据存储,默认会话时间30分钟
	 *
	 * @param vertx vertx实例
	 */
	public static SessionDataStore instance(Vertx vertx) {
		return instance(vertx, DEFAULT_TIMEOUT);
	}

  /**
	 * 创建一个会话数据存储
	 *
	 * @param vertx vertx实例
	 * @param timeout
	 *          会话超时时间<=0为不限制
	 */
	public static SessionDataStore instance(Vertx vertx, long timeout) {
		if (INSTANCE == null) {
			synchronized (SessionDataStore.class) {
				if (INSTANCE == null) {
					INSTANCE = new SessionDataStore(vertx, timeout);
				}
			}
		}
		return INSTANCE;
	}
	/** 对象单例 */
	private volatile static SessionDataStore INSTANCE;
	/**
	 * 初始化
	 *
	 * @param vertx vertx实例
	 * @param timeout
	 *          会话超时时间
	 */
	private SessionDataStore(Vertx vertx, long timeout) {
		super();
		this.vertx = vertx;
		this.timeout = timeout;
		this.localMap = vertx.sharedData().getLocalMap("ThisVertxSessionDataStoreLocalMap");
		this.timer();
	}

	/** 默认的清理数据时间,默认1 */
	public final static long DEFAULT_CLEAR_INTERVAL = 1000;
	/** 默认的清理数据时间,默认30分钟 */
	public final static long DEFAULT_TIMEOUT = 1000 * 60 * 30;

	/** vertx */
	private final Vertx vertx;
	/** 数据超时时间 */
	private final long timeout;
	/** 用户数据 */
	private final Map<String, Session> localMap;
	/**
	 * 添加数据
	 *
	 * @param sessionId
	 *          会话的id
	 * @param key
	 *          数据的id
	 * @param value
	 *          数据
	 */
	public SessionDataStore put(String sessionId, String key, Object value) {
		synchronized (localMap) {
			Session session = localMap.computeIfAbsent(sessionId, n -> new Session(sessionId).put(key, value));
			session.put(key, value);
		}
		return this;
	}

	/**
	 * 获取数据
	 *
	 * @param sessionId
	 *          会话的id
	 * @param key
	 *          数据的id
	 */
	public <T> T get(String sessionId, String key) {
		if (sessionId==null) {
			return null;
		}
		Session session = localMap.get(sessionId);
		if (session == null) {
			return null;
		}
		session.setLastAccessed(System.currentTimeMillis());
		return session.get(key);
	}
	/**
	 * 删除数据
	 *
	 * @param sessionId
	 *          会话的id
	 * @param key
	 *          数据的id
	 */
	public <T> T remove(String sessionId, String key) {
		if (sessionId==null) {
			return null;
		}
		Session session = localMap.get(sessionId);
		if (session == null) {
			return null;
		}
		session.setLastAccessed(System.currentTimeMillis());
		return session.remove(key);
	}
	/**
	 * 销毁会话,如果存在会话并销毁返回true,不存在会话返回false
	 *
	 * @param sessionId
	 *          会话的id
	 */
	public boolean destroy(String sessionId) {
		if (sessionId==null) {
			return false;
		}
		Session remove = localMap.remove(sessionId);
		return remove != null;
	}
	/**
	 * 清理过期的数据
	 *
	 */
	private synchronized void clearStale(long tid) {
		if (!localMap.isEmpty()) {
			long now = System.currentTimeMillis();
			Set<String> toRemove = new HashSet<>();
			for (Session session : localMap.values()) {
				if (now - session.getLastAccessed() > timeout) {
					toRemove.add(session.id());
				}
			}
			for (String id : toRemove) {
				localMap.remove(id);
			}
		}
		timer();
	}
	/**
	 * 清理数据的执行器
	 */
	private synchronized void timer() {
		if (timeout > 0) {
			vertx.setTimer(DEFAULT_CLEAR_INTERVAL, this::clearStale);
		}
	}

	/**
	 * 会话类
	 *
	 * YU
	 *
	 */
	 class Session implements Shareable {
		/** id */
		private final String id;
		/** 最后一次访问的时间 */
		private long lastAccessed;
		/** 会话数据 */
		private final Map<String, Object> data = new ConcurrentHashMap<>();
		/**
		 * 初始化
		 *
		 * @param id
		 *          唯一的用户id
		 */
		public Session(String id) {
			super();
			this.id = id;
			this.lastAccessed = System.currentTimeMillis();
		}
		/**
		 * 添加数据
		 *
		 * @param key
		 *          数据的id
		 * @param value
		 *          数据
		 */
		public Session put(String key, Object value) {
			if (value == null) {
				data.remove(key);
			} else {
				data.put(key, value);
			}
			return this;
		}
		/**
		 * 获取数据
		 *
		 * @param key
		 *          数据的id
		 */
		@SuppressWarnings("unchecked")
		public <T> T get(String key) {
			if (data.get(key) == null) {
				return null;
			}
			return (T) data.get(key);
		}
		/**
		 * 删除数据
		 *
		 * @param key
		 *          数据的id
		 */
		@SuppressWarnings("unchecked")
		public <T> T remove(String key) {
			Object object = data.remove(key);
			if (object == null) {
				return null;
			} else {
				return (T) object;
			}
		}
		/**
		 * 获取该会话的id
		 *
		 */
		public String id() {
			return id;
		}
		/**
		 * 获取最后一次访问的时间
		 *
		 */
		public long getLastAccessed() {
			return lastAccessed;
		}
		/**
		 * 设置最后一次访问的时间
		 *
		 * @param lastAccessed 最后访问时间
		 */
    public Session setLastAccessed(long lastAccessed) {
			this.lastAccessed = lastAccessed;
			return this;
		}
		@Override
		public String toString() {
			return "Session [id=" + id + ", lastAccessed=" + lastAccessed + ", data=" + data + "]";
		}
	}
}
