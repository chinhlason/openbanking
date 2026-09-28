package vn.com.truongsonbank.shared.cache;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

class CacheKeyBuilder {
    private final CacheProperties properties;
    private final ExpressionParser parser = new SpelExpressionParser();

    CacheKeyBuilder(CacheProperties properties) {
        this.properties = properties;
    }

    String key(String cacheName, String keyExpression, ProceedingJoinPoint joinPoint) {
        String rawKey = keyExpression == null || keyExpression.isBlank()
                ? Arrays.deepToString(joinPoint.getArgs())
                : String.valueOf(evaluate(keyExpression, joinPoint.getArgs()));
        return properties.getKeyPrefix() + ":" + cacheName + ":" + rawKey;
    }

    String prefix(String cacheName) {
        return properties.getKeyPrefix() + ":" + cacheName + ":";
    }

    private Object evaluate(String keyExpression, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }
        return parser.parseExpression(keyExpression).getValue(context);
    }
}
