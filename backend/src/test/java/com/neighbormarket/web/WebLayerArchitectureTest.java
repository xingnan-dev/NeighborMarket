package com.neighbormarket.web;

import com.neighbormarket.service.CreateItemCommand;
import com.neighbormarket.service.ItemService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WebLayerArchitectureTest {

    @Test
    void serviceAcceptsAServiceCommandInsteadOfAControllerType() {
        Method create = Arrays.stream(ItemService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("create"))
                .findFirst()
                .orElseThrow();

        assertThat(create.getParameterTypes())
                .containsExactly(CreateItemCommand.class, String.class);
        assertThat(Arrays.stream(create.getParameterTypes()))
                .noneMatch(type -> type.getPackageName().startsWith("com.neighbormarket.web"));
    }

    @Test
    void controllersDoNotDeclareTransportRecordsInline() {
        assertThat(ItemController.class.getDeclaredClasses()).isEmpty();
        assertThat(ExchangeController.class.getDeclaredClasses()).isEmpty();
        assertThat(AuthController.class.getDeclaredClasses()).isEmpty();
    }
}
