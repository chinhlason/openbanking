package vn.com.truongsonbank.shared.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import vn.com.truongsonbank.shared.exception.TsbException;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

@Aspect
class InternalAuthorizationAspect {
    @Around("@within(vn.com.truongsonbank.shared.security.RequireRole) || @annotation(vn.com.truongsonbank.shared.security.RequireRole) || "
            + "@within(vn.com.truongsonbank.shared.security.RequireScope) || @annotation(vn.com.truongsonbank.shared.security.RequireScope)")
    public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> type = joinPoint.getTarget().getClass();
        AuthContext context = AuthContextHolder.current()
                .orElseThrow(() -> new TsbException(InternalAuthErrors.MISSING));
        RequireRole role = first(AnnotatedElementUtils.findMergedAnnotation(method, RequireRole.class),
                AnnotatedElementUtils.findMergedAnnotation(type, RequireRole.class));
        RequireScope scope = first(AnnotatedElementUtils.findMergedAnnotation(method, RequireScope.class),
                AnnotatedElementUtils.findMergedAnnotation(type, RequireScope.class));
        if (role != null && !matches(context.roles(), role.value(), role.mode())) {
            throw new TsbException(InternalAuthErrors.FORBIDDEN);
        }
        if (scope != null && !matches(context.scopes(), scope.value(), scope.mode())) {
            throw new TsbException(InternalAuthErrors.FORBIDDEN);
        }
        return joinPoint.proceed();
    }

    private <T> T first(T method, T type) {
        return method == null ? type : method;
    }

    private boolean matches(Collection<String> actual, String[] required, MatchMode mode) {
        if (required == null || required.length == 0) {
            return true;
        }
        Set<String> owned = Set.copyOf(actual);
        if (mode == MatchMode.ALL) {
            for (String item : required) {
                if (!owned.contains(item)) {
                    return false;
                }
            }
            return true;
        }
        for (String item : required) {
            if (owned.contains(item)) {
                return true;
            }
        }
        return false;
    }
}
