package ru.wisla.fm.processing.adapter.out.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Recording stand-in for {@link EventJpaRepository} used by the group-1 characterization tests.
 * Built with a JDK proxy rather than Mockito, matching the JDK 25 convention already established
 * by {@code RawEventKafkaListenerTest}. It records which derived query the persistence adapter
 * picked and with which arguments, and answers with canned results per method name.
 */
final class CharacterizationEventRepository implements InvocationHandler {

    record Call(String method, List<Object> args) {
    }

    private final List<Call> calls = new ArrayList<>();
    private final Map<String, Object> stubs = new HashMap<>();

    EventJpaRepository asRepository() {
        return (EventJpaRepository) Proxy.newProxyInstance(
                EventJpaRepository.class.getClassLoader(),
                new Class<?>[]{EventJpaRepository.class},
                this);
    }

    CharacterizationEventRepository stub(String method, Object result) {
        stubs.put(method, result);
        return this;
    }

    List<String> methodNames() {
        return calls.stream().map(Call::method).toList();
    }

    List<String> queryNames(String prefix) {
        return methodNames().stream().filter(name -> name.startsWith(prefix)).toList();
    }

    Call callTo(String method) {
        return calls.stream()
                .filter(call -> call.method().equals(method))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected a call to " + method + " but recorded " + methodNames()));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        switch (name) {
            case "toString" -> {
                return "CharacterizationEventRepository";
            }
            case "hashCode" -> {
                return System.identityHashCode(proxy);
            }
            case "equals" -> {
                return proxy == args[0];
            }
            default -> calls.add(new Call(name, args == null ? List.of() : Arrays.asList(args)));
        }

        if (stubs.containsKey(name)) {
            return stubs.get(name);
        }
        if ("save".equals(name)) {
            return args[0];
        }
        Class<?> returnType = method.getReturnType();
        if (Optional.class.equals(returnType)) {
            return Optional.empty();
        }
        if (List.class.equals(returnType)) {
            return List.of();
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (void.class.equals(returnType)) {
            return null;
        }
        throw new UnsupportedOperationException("no canned result for " + name);
    }
}
