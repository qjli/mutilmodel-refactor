package io.agentscope.demo.app.session;

import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.State;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 延迟创建底层 {@link Session}，避免应用启动阶段因 Redis 尚未就绪而失败；首次 load/save 时再连接。
 */
public final class LazyInitializingSession implements Session {

    private static final Logger log = LoggerFactory.getLogger(LazyInitializingSession.class);

    /** 由配置类提供，通常为 {@code () -> RedisSession.builder()...build()}。 */
    private final Supplier<Session> delegateFactory;
    /** 双重检查锁下的单例委托；volatile 保证多线程可见性。 */
    private volatile Session delegate;

    public LazyInitializingSession(Supplier<Session> delegateFactory) {
        this.delegateFactory = delegateFactory;
    }

    /** 懒加载：仅首次持久化访问时创建真实 RedisSession 并打日志。 */
    private Session delegate() {
        Session local = delegate;
        if (local == null) {
            synchronized (this) {
                local = delegate;
                if (local == null) {
                    log.info("[agentscope-session] initializing redis session (first use)");
                    local = delegateFactory.get();
                    delegate = local;
                }
            }
        }
        return local;
    }

    @Override
    public void save(SessionKey sessionKey, String key, State value) {
        delegate().save(sessionKey, key, value);
    }

    @Override
    public void save(SessionKey sessionKey, String key, List<? extends State> values) {
        delegate().save(sessionKey, key, values);
    }

    @Override
    public <T extends State> Optional<T> get(SessionKey sessionKey, String key, Class<T> type) {
        return delegate().get(sessionKey, key, type);
    }

    @Override
    public <T extends State> List<T> getList(SessionKey sessionKey, String key, Class<T> itemType) {
        return delegate().getList(sessionKey, key, itemType);
    }

    @Override
    public boolean exists(SessionKey sessionKey) {
        return delegate().exists(sessionKey);
    }

    @Override
    public void delete(SessionKey sessionKey) {
        delegate().delete(sessionKey);
    }

    @Override
    public Set<SessionKey> listSessionKeys() {
        return delegate().listSessionKeys();
    }

    @Override
    public void close() {
        Session local = delegate;
        if (local != null) {
            local.close();
        }
    }
}
