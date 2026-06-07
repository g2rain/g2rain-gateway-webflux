package com.g2rain.gateway.route;

import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.matcher.MatchEngine;
import com.g2rain.gateway.matcher.RuleCompiler;
import com.g2rain.gateway.matcher.RuleDefinition;
import com.g2rain.gateway.matcher.RuleTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 路由「粗筛索引」持有器：与动态路由控制面同步，向 WebFlux 网关暴露按方法+路径的匹配能力。
 *
 * <p>Spring Cloud Gateway 在解析请求时需要根据 {@link org.springframework.http.HttpMethod} 与路径
 * 找到候选 {@link org.springframework.cloud.gateway.route.Route}。本类不直接持有 SCG 的 {@code Route}，
 * 而是持有与路径编译规则一致的 {@link RuleDefinition}{@code <Long>}，并用 {@link MatchEngine}{@code <Long>}
 * 将「方法 + 路径」映射为<strong>路由 id（{@code Long}）</strong>。上层再用该 id 从运行时路由快照中取完整
 * {@code Route} 并执行谓词细判。</p>
 *
 * <p>对 Web 层隐藏 {@link MatchEngine.CandidateVisitor}：调用方只需提供
 * {@code Function<Long, Mono<R>>}，由本类适配为引擎的访问器（{@code rule -> func.apply(rule.target())}）。</p>
 *
 * <p><strong>线程与一致性：</strong>{@link #replace}、{@link #upsert}、{@link #remove} 使用 {@code synchronized}，
 * 保证「注册表 + 引擎快照」在同一监视器下更新，避免并发下索引与真相源不一致。请求期的 {@link #matchRoute}
 * 仅读取引擎与注册表引用（引擎内部用原子快照 + 缓存），与写路径互斥由上述同步方法保证。</p>
 *
 * @author alpha
 * @since 2026/05/07
 */
@Slf4j
@Component
public class RouteMatchHolder {

    /**
     * 路径匹配引擎：目标类型为路由 id（{@code Long}），与 {@link RuleDefinition} 的 {@code target} 一致。
     *
     * <p>容量 {@code 20_000} 为请求级匹配缓存条数上限（见 {@link MatchEngine} 构造说明）。</p>
     */
    private final MatchEngine<Long> engine = new MatchEngine<>(20_000);

    /**
     * 将 {@link RuleDefinition} 编译进 {@link RuleTable} 的编译器（路径槽位、方法位图等）。
     */
    private final RuleCompiler<Long> compiler = new RuleCompiler<>();

    /**
     * 当前已知的路由定义注册表：key 为路由 id，用于 upsert/remove 时取旧定义以计算版本失效范围。
     *
     * <p>与 {@link MatchEngine} 内表共同构成「控制面真相源」；{@link #replace} 会清空后重建。</p>
     */
    private final Map<Long, RuleDefinition<Long>> registry = new ConcurrentHashMap<>();

    /**
     * 按 HTTP 方法与原始路径做匹配，并由调用方对「候选路由 id」做细判（通常为异步谓词）。
     *
     * <p>流程语义：引擎按 exact → bucket → global 顺序产出候选 {@link com.g2rain.gateway.matcher.MatchRule}；
     * 对每条候选调用 {@code func.apply(rule.target())}。若 {@code Mono} 为空，引擎继续下一条候选；
     * 若发出非空值，则该值作为整个匹配的返回值（短路）。</p>
     *
     * @param method  请求方法，不可为 {@code null}（否则引擎返回空）
     * @param rawPath 未标准化或已带前缀的原始路径，由引擎内部与 {@link com.g2rain.gateway.matcher.MatcherUtils} 对齐
     * @param func    由路由 id 返回异步判定结果；接受该 id 时返回非空 {@link Mono}，否则 {@link Mono#empty()}
     * @param <R>     细判成功时的结果类型（例如 {@link org.springframework.cloud.gateway.route.Route}）
     * @return 首个通过细判的结果；全程无命中或细判均拒绝时为 {@link Mono#empty()}
     */
    public <R> Mono<R> matchRoute(HttpMethod method, String rawPath, Function<Long, Mono<R>> func) {
        return engine.matchAndVisit(method, rawPath, rule -> func.apply(rule.target()));
    }

    /**
     * 全量替换：清空本地注册表并用给定规则集合重新编译引擎内路由表。
     *
     * <p>典型调用场景：网关启动全量拉取、或控制面下发完整快照。会跳过 id 为空的无效项。</p>
     *
     * @param rules 新的路由定义集合；允许为空或 null 元素，空集合等价于安装空表
     */
    public synchronized void replace(Collection<RuleDefinition<Long>> rules) {
        registry.clear();

        if (Collections.isEmpty(rules)) {
            engine.replace(compiler.compile(List.of()));
            return;
        }

        // 先过滤并写入注册表，再一次性 compile，避免引擎处于半更新状态被读路径看到
        List<RuleDefinition<Long>> definitions = new ArrayList<>();
        for (RuleDefinition<Long> d : rules) {
            if (Objects.isNull(d) || Objects.isNull(d.id())) {
                continue;
            }

            registry.put(d.id(), d);
            definitions.add(d);
        }

        engine.replace(compiler.compile(definitions));
    }

    /**
     * 插入或更新单条路由定义，并增量更新引擎（仅失效受影响的 scope 版本，不必清空全表缓存）。
     *
     * @param next 新路由定义；id 为空时记录告警并忽略
     */
    public synchronized void upsert(RuleDefinition<Long> next) {
        if (Objects.isNull(next) || Objects.isNull(next.id())) {
            log.warn("忽略无效路由索引 upsert: {}", next);
            return;
        }

        RuleDefinition<Long> old = registry.put(next.id(), next);
        engine.update(table -> {
            RuleTable<Long> t = table;
            if (Objects.nonNull(old)) {
                t = compiler.remove(t, old);
            }
            return compiler.upsert(t, next);
        }, changedScopes(old, next));
    }

    /**
     * 按路由 id 删除：若注册表中不存在则直接返回。
     *
     * @param routeId 路由主键
     */
    public synchronized void remove(Long routeId) {
        if (Objects.isNull(routeId)) {
            return;
        }

        RuleDefinition<Long> removed = registry.remove(routeId);
        if (Objects.isNull(removed)) {
            return;
        }

        engine.update(table -> compiler.remove(table, removed), changedScopes(removed, null));
    }

    /**
     * 汇总一次 upsert 所涉及的「版本戳失效范围」，供 {@link MatchEngine#update}  bump 局部版本。
     *
     * <p>旧规则与新规则各自通过 {@link RuleCompiler#describe} 解析出
     * {@link RuleCompiler.RuleScope}，再转为 {@link MatchEngine.ScopeVersion} 列表。</p>
     *
     * @param previous 更新前的定义，可为 {@code null}（纯新增）
     * @param current  更新后的定义，可为 {@code null}（纯删除路径上一般由 remove 处理）
     * @return 需要去重的 scope 集合（当前实现为 {@link List}，引擎侧 bump 会归并）
     */
    private Collection<MatchEngine.ScopeVersion> changedScopes(RuleDefinition<Long> previous, RuleDefinition<Long> current) {
        List<MatchEngine.ScopeVersion> scopes = new ArrayList<>();
        if (Objects.nonNull(previous)) {
            compiler.describe(previous).ifPresent(scope -> appendScope(scopes, scope));
        }

        if (Objects.nonNull(current)) {
            compiler.describe(current).ifPresent(scope -> appendScope(scopes, scope));
        }

        return scopes;
    }

    /**
     * 将单条规则对应的 {@link RuleCompiler.RuleScope} 展开为若干 {@link MatchEngine.ScopeVersion}。
     *
     * <p>ANY 方法规则只产生一条 {@link RuleTable#ALL_METHOD_MASK} 的 scope；否则按方法位逐个展开。</p>
     *
     * @param scopes 待追加的集合
     * @param scope  编译器给出的单条范围描述
     */
    private void appendScope(Collection<MatchEngine.ScopeVersion> scopes, RuleCompiler.RuleScope scope) {
        if (scope.isAnyMethod()) {
            scopes.add(toScopeVersion(RuleTable.ALL_METHOD_MASK, scope));
            return;
        }

        for (int bit : scope.methodBits()) {
            scopes.add(toScopeVersion(bit, scope));
        }
    }

    /**
     * 将编译器槽位类型映射为引擎用于版本控制的 {@link MatchEngine.ScopeVersion}。
     *
     * @param scopeMethod HTTP 方法位或 {@link RuleTable#ALL_METHOD_MASK}
     * @param scope       exact / bucket / global 及对应 key
     * @return 引擎可识别的版本键
     */
    private MatchEngine.ScopeVersion toScopeVersion(int scopeMethod, RuleCompiler.RuleScope scope) {
        return switch (scope.slotType()) {
            case EXACT -> MatchEngine.ScopeVersion.exact(scopeMethod, scope.key());
            case BUCKET -> MatchEngine.ScopeVersion.bucket(scopeMethod, scope.key());
            case GLOBAL -> MatchEngine.ScopeVersion.global(scopeMethod);
        };
    }
}
