package com.ofertas.bot;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(properties={"bot.scheduling-enabled=false","logging.file.name=target/test.log","bot.dry-run=true",
    "bot.amazon.enabled=false","bot.mercado-livre.enabled=false","spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/bot_ofertas_test}",
    "spring.datasource.username=${TEST_DB_USER:bot_test}","spring.datasource.password=${TEST_DB_PASSWORD:}"})
class BotOfertasApplicationTests { @Test void contextLoads() {} }
