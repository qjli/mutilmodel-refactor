/**
 * 本项目的会话<strong>集成层</strong>：延迟初始化包装、coverage 侧车等。
 *
 * <p><b>与官方包名的区别</b>：
 *
 * <ul>
 *   <li>{@code io.agentscope.core.session.redis.*}（如 {@code RedisSession}）来自 Maven 构件
 *       {@code agentscope-extensions-session-redis}，在 {@code ~/.m2} 或依赖 jar 内，<strong>不是</strong>本仓库源码。
 *   <li>本仓库业务代码包为 {@code io.agentscope.demo.*}；若在仓库根目录看到 {@code io/agentscope/core/...}
 *       多为误解压的依赖源码，应删除并已加入 {@code .gitignore}。
 * </ul>
 *
 * <p>Spring 装配入口：{@link io.agentscope.demo.app.config.AgentscopeRedisSessionConfiguration}、
 * {@link io.agentscope.demo.app.config.AgentscopeFileSessionConfiguration}。
 */
package io.agentscope.demo.app.session;
