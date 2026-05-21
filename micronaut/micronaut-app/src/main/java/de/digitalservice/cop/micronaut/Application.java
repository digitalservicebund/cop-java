package de.digitalservice.cop.micronaut;

import io.micronaut.runtime.Micronaut;

/**
 * No {@code @SpringBootApplication} needed — there is no classpath scan to configure.
 * The annotation processor already wrote all DI wiring into generated {@code $Definition} classes at compile time.
 */
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
