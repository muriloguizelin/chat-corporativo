package br.ufmt.sd.chat.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de Entrada da Aplicação Web Spring Boot + Thymeleaf.
 * 
 * Disponibiliza a interface Web corporativa para a Federação,
 * conectando-se ao Servidor Broker TCP via Socket interno.
 */
@SpringBootApplication
public class WebChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebChatApplication.class, args);
    }
}
