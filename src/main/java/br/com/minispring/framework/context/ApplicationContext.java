package br.com.minispring.framework.context;

import br.com.minispring.framework.annotation.Value;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;

/** Container IoC: um singleton por classe, com injeção pelo único construtor público. */
public final class ApplicationContext {

    private final Set<Class<?>> definitions;
    private final Map<Class<?>, Object> singletons = new LinkedHashMap<>();

    private final Deque<Class<?>> creating = new ArrayDeque<>();
    private final AppConfig config;

    public ApplicationContext(Set<Class<?>> definitions, AppConfig config) {
        this.definitions = new LinkedHashSet<>(definitions);
        this.config = config;

        this.definitions.stream().sorted(Comparator.comparing(Class::getName)).forEach(this::getBean);
    }

    public synchronized <T> T getBean(Class<T> requestedType) {

        var candidates = definitions.stream().filter(requestedType::isAssignableFrom).toList();
        if (candidates.isEmpty()) throw new IllegalStateException("Dependência não registrada: " + requestedType.getName());
        if (candidates.size() > 1) throw new IllegalStateException("Dependência ambígua: " + requestedType.getName() + " -> " + candidates);
        Class<?> type = candidates.getFirst();

        if (singletons.containsKey(type)) return requestedType.cast(singletons.get(type));

        if (creating.contains(type)) throw new IllegalStateException("Dependência circular: " + creating + " -> " + type.getSimpleName());
        creating.addLast(type);
        try {
            var constructors = type.getConstructors();
            if (constructors.length != 1) throw new IllegalStateException("Componente deve ter um único construtor público: " + type.getName());
            var constructor = constructors[0];

            Object[] arguments = Arrays.stream(constructor.getParameters()).map(this::resolve).toArray();

            Object bean = constructor.newInstance(arguments);
            singletons.put(type, bean);
            return requestedType.cast(bean);

        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Construtor falhou: " + type.getName(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Não foi possível criar " + type.getName(), e);
        } finally {

            creating.removeLast();
        }
    }

    private Object resolve(Parameter parameter) {

        var value = parameter.getAnnotation(Value.class);
        if (value == null) return getBean(parameter.getType());
        String raw = config.get(value.value());
        if (parameter.getType() == String.class) return raw;
        if (parameter.getType() == int.class || parameter.getType() == Integer.class) return Integer.valueOf(raw);
        throw new IllegalStateException("@Value suporta String e int: " + parameter);
    }

    public Collection<Object> beans() { return List.copyOf(singletons.values()); }
}
