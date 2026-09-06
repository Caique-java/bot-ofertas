package com.ofertas.bot.util;

import com.ofertas.bot.model.Marketplace;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/** Source links are preserved; affiliate parameters are never invented. */
@Component
public class AffiliateLinkConverter {
    public String validar(String url, Marketplace source, String partnerTag) {
        var uri = URI.create(url);
        var hosts = source == Marketplace.AMAZON ? Set.of("www.amazon.com.br", "amazon.com.br")
                : Set.of("produto.mercadolivre.com.br", "www.mercadolivre.com.br", "mercadolivre.com.br");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || !hosts.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443))
            throw new IllegalArgumentException("Destino de compra inválido");
        if (source == Marketplace.AMAZON) {
            var tags = Arrays.stream((uri.getRawQuery() == null ? "" : uri.getRawQuery()).split("&"))
                    .map(x -> x.split("=", 2)).filter(x -> x.length == 2 && x[0].equals("tag"))
                    .map(x -> URLDecoder.decode(x[1], StandardCharsets.UTF_8)).toList();
            if (partnerTag == null || partnerTag.isBlank() || tags.size() != 1 || !tags.get(0).equals(partnerTag))
                throw new IllegalArgumentException("Tag Amazon ausente ou divergente");
        }
        return url;
    }
    public boolean imagemPermitida(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            var uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && Set.of("m.media-amazon.com", "http2.mlstatic.com").contains(uri.getHost());
        } catch (IllegalArgumentException e) { return false; }
    }
}
