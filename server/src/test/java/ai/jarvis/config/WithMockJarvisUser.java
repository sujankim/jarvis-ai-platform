package ai.jarvis.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.test.context.support.WithSecurityContext;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockJarvisUserSecurityContextFactory.class)
public @interface WithMockJarvisUser {

    String principal();

    String[] roles() default { "USER" };

    String[] authorities() default {};
}
