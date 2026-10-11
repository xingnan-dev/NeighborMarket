package com.neighbormarket.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeServiceArchitectureTest {

    @Test
    void facadeDependsOnDomainServicesInsteadOfMappers() {
        Set<Class<?>> fieldTypes = Set.of(
                PickupOptionService.class,
                InterestWorkflowService.class,
                HandoverWorkflowService.class,
                CommentWorkflowService.class,
                PersonalExchangeService.class);

        assertThat(ExchangeService.class.getDeclaredFields())
                .extracting(Field::getType)
                .containsExactlyInAnyOrderElementsOf(fieldTypes)
                .noneMatch(BaseMapper.class::isAssignableFrom);
    }

    @Test
    void transactionBoundariesLiveOnTheServicesThatPerformWrites() throws Exception {
        Map<Class<?>, Set<String>> transactionalMethods = Map.of(
                PickupOptionService.class, Set.of("configure"),
                InterestWorkflowService.class, Set.of("want", "cancel"),
                HandoverWorkflowService.class, Set.of("confirm", "cancel", "complete"),
                CommentWorkflowService.class, Set.of("comment", "reply"));

        for (Map.Entry<Class<?>, Set<String>> entry : transactionalMethods.entrySet()) {
            Set<String> annotated = Set.of(entry.getKey().getDeclaredMethods()).stream()
                    .filter(method -> method.isAnnotationPresent(Transactional.class))
                    .map(Method::getName)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(annotated).isEqualTo(entry.getValue());
        }

        assertThat(Set.of(ExchangeService.class.getDeclaredMethods()))
                .noneMatch(method -> method.isAnnotationPresent(Transactional.class));
    }
}
