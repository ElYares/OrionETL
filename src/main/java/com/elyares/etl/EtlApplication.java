package com.elyares.etl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada principal de la aplicación OrionETL.
 *
 * <p>Esta clase arranca el contexto de Spring Boot y registra todos los
 * componentes declarados en el paquete {@code com.elyares.etl} y sus
 * subpaquetes. La anotación {@link SpringBootApplication} habilita la
 * autoconfiguración, el escaneo de componentes y la configuración de Spring.</p>
 */
@SpringBootApplication
public class EtlApplication {

    /**
     * Método principal de arranque de la aplicación.
     *
     * <p>Delega el inicio del contexto de Spring Boot a
     * {@link SpringApplication#run(Class, String[])}.</p>
     *
     * @param args argumentos de línea de comandos pasados al proceso JVM
     */
    public static void main(String[] args) {
        SpringApplication.run(EtlApplication.class, args);
    }
}
