package vn.com.truongsonbank.shared.protocol;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

public class TsbHttpClientRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, ResourceLoaderAware {
    private Environment environment;
    private ResourceLoader resourceLoader;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        if (!environment.getProperty("tsb.shared.protocol.enabled", Boolean.class, true)) {
            return;
        }

        Set<String> basePackages = basePackages(metadata);
        ProtocolProperties properties = Binder.get(environment)
                .bind("tsb.shared.protocol", ProtocolProperties.class)
                .orElseGet(ProtocolProperties::new);

        for (String className : scan(basePackages)) {
            Class<?> interfaceType = resolveClass(className);
            TsbHttpClient annotation = interfaceType.getAnnotation(TsbHttpClient.class);
            if (annotation == null || !interfaceType.isInterface()) {
                continue;
            }
            validateInterface(interfaceType, annotation, properties);
            registerClientBean(registry, interfaceType, annotation.downstream().trim());
        }
    }

    private Set<String> basePackages(AnnotationMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(EnableTsbHttpClients.class.getName());
        String[] configured = attributes == null ? new String[0] : (String[]) attributes.get("basePackages");
        Set<String> packages = new LinkedHashSet<>();
        Arrays.stream(configured == null ? new String[0] : configured)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(packages::add);
        if (packages.isEmpty()) {
            packages.add(ClassUtils.getPackageName(metadata.getClassName()));
        }
        return packages;
    }

    private Set<String> scan(Set<String> basePackages) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment) {
                    @Override
                    protected boolean isCandidateComponent(MetadataReader metadataReader) {
                        return metadataReader.getClassMetadata().isIndependent();
                    }

                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isIndependent();
                    }
                };
        // Match only a direct @TsbHttpClient declaration. AnnotationTypeFilter can
        // still match the registrar's enable annotation through metadata traversal
        // in a nested Spring Boot jar, so inspect the direct annotation metadata.
        scanner.addIncludeFilter((metadataReader, metadataReaderFactory) ->
                metadataReader.getAnnotationMetadata().getAnnotationTypes()
                        .contains(TsbHttpClient.class.getName()));
        if (resourceLoader != null) {
            scanner.setResourceLoader(resourceLoader);
        }

        Set<String> classNames = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            scanner.findCandidateComponents(basePackage)
                    .forEach(candidate -> {
                        if (candidate.getBeanClassName() != null) {
                            classNames.add(candidate.getBeanClassName());
                        }
                    });
        }
        return classNames;
    }

    private Class<?> resolveClass(String className) {
        try {
            ClassLoader classLoader = resourceLoader == null ? null : resourceLoader.getClassLoader();
            return ClassUtils.forName(className, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Cannot load @TsbHttpClient type " + className, ex);
        }
    }

    private void validateInterface(Class<?> interfaceType, TsbHttpClient annotation, ProtocolProperties properties) {
        if (!interfaceType.isInterface()) {
            throw new IllegalStateException("@TsbHttpClient must be placed on an interface: " + interfaceType.getName());
        }
        if (annotation == null || !StringUtils.hasText(annotation.downstream())) {
            throw new IllegalStateException("@TsbHttpClient downstream must not be blank for " + interfaceType.getName());
        }

        String downstream = annotation.downstream().trim();
        ProtocolProperties.Downstream config = properties.getDownstreams().get(downstream);
        if (config == null) {
            throw new IllegalStateException("@TsbHttpClient " + interfaceType.getName()
                    + " references unknown downstream '" + downstream + "'");
        }
        if (!config.isEnabled()) {
            throw new IllegalStateException("@TsbHttpClient " + interfaceType.getName()
                    + " references disabled downstream '" + downstream + "'");
        }
        if (!StringUtils.hasText(config.getBaseUrl()) && !StringUtils.hasText(config.getTarget())
                && !StringUtils.hasText(config.getServiceId())) {
            throw new IllegalStateException("@TsbHttpClient " + interfaceType.getName()
                    + " downstream '" + downstream + "' must define base-url, target, or service-id");
        }

        boolean hasExchangeMethod = false;
        for (Method method : interfaceType.getMethods()) {
            if (skipValidation(method)) {
                continue;
            }
            if (!hasSupportedExchange(method)) {
                throw new IllegalStateException("@TsbHttpClient " + interfaceType.getName()
                        + " method " + method.getName() + " must use a supported Spring HTTP exchange annotation");
            }
            hasExchangeMethod = true;
        }
        if (!hasExchangeMethod) {
            throw new IllegalStateException("@TsbHttpClient interface has no HTTP exchange methods: "
                    + interfaceType.getName());
        }
    }

    private boolean skipValidation(Method method) {
        int modifiers = method.getModifiers();
        return method.isDefault()
                || method.isSynthetic()
                || method.isBridge()
                || Modifier.isStatic(modifiers)
                || method.getDeclaringClass() == Object.class;
    }

    private boolean hasSupportedExchange(Method method) {
        return method.isAnnotationPresent(GetExchange.class)
                || method.isAnnotationPresent(PostExchange.class)
                || method.isAnnotationPresent(PutExchange.class)
                || method.isAnnotationPresent(PatchExchange.class)
                || method.isAnnotationPresent(DeleteExchange.class)
                || method.isAnnotationPresent(HttpExchange.class);
    }

    private void registerClientBean(BeanDefinitionRegistry registry, Class<?> interfaceType, String downstream) {
        String beanName = "tsbHttpClient:" + interfaceType.getName();
        if (registry.containsBeanDefinition(beanName)) {
            throw new IllegalStateException("Duplicate @TsbHttpClient registration for " + interfaceType.getName());
        }
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(TsbHttpClientFactoryBean.class)
                .addConstructorArgValue(downstream)
                .addConstructorArgValue(interfaceType);
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }
}
