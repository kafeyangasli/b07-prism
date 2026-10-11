package com.github.kafeyangasli.prism.support;

import java.lang.annotation.*;
import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = PrismSecurityContextFactory.class,
        setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION)
public @interface WithPrismUser {
    String username() default "user";
    String[] roles() default {"PENGGUNA"};
}
