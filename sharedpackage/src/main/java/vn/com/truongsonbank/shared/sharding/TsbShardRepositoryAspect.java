package vn.com.truongsonbank.shared.sharding;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TsbShardRepositoryAspect {
    private final TsbShardResolver resolver;
    private final TsbShardTableManager tableManager;

    public TsbShardRepositoryAspect(TsbShardResolver resolver, TsbShardTableManager tableManager) {
        this.resolver = resolver;
        this.tableManager = tableManager;
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository+.save(..))")
    public Object routeSave(ProceedingJoinPoint joinPoint) throws Throwable {
        Object entity = joinPoint.getArgs().length == 0 ? null : joinPoint.getArgs()[0];
        if (entity == null || !entity.getClass().isAnnotationPresent(ShardEntity.class) || TsbShardContext.current().isPresent()) {
            return joinPoint.proceed();
        }
        ShardRoute route = resolver.resolve(entity);
        tableManager.ensureTable(route);
        return TsbShardContext.runWith(route, () -> proceed(joinPoint));
    }

    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }
}
