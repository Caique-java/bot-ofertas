package com.ofertas.bot.client;
import com.ofertas.bot.config.BotProperties;
import com.ofertas.bot.model.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
public interface MarketplaceClient {
    Marketplace marketplace();
    BotProperties.Source settings();
    void collect(Consumer<OfertaDTO> consumer);
    Optional<OfertaDTO> refresh(OfertaDTO candidate);
}
