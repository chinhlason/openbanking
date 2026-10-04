package vn.com.truongsonbank.shared.protocol;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;

public class TsbHttpClientFactoryBean<T> implements FactoryBean<T>, BeanFactoryAware, InitializingBean {
    private final String downstream;
    private final Class<T> interfaceType;
    private BeanFactory beanFactory;
    private T client;

    public TsbHttpClientFactoryBean(String downstream, Class<T> interfaceType) {
        this.downstream = downstream;
        this.interfaceType = interfaceType;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(downstream)) {
            throw new IllegalStateException("@TsbHttpClient downstream must not be blank for " + interfaceType.getName());
        }
        if (!interfaceType.isInterface()) {
            throw new IllegalStateException("@TsbHttpClient can only create interface proxies: " + interfaceType.getName());
        }
        client = beanFactory.getBean(TsbHttpClientFactory.class).httpInterface(downstream, interfaceType);
    }

    @Override
    public T getObject() {
        return client;
    }

    @Override
    public Class<?> getObjectType() {
        return interfaceType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
