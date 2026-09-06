package com.ofertas.bot.config;
import com.ofertas.bot.repository.QueueRepository;
import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
@Component("pipeline")
public class PipelineHealth implements HealthIndicator {
    private final QueueRepository queue; private final BotProperties p; private final Clock clock;
    public PipelineHealth(QueueRepository queue,BotProperties p,Clock clock) { this.queue=queue;this.p=p;this.clock=clock; }
    @SuppressWarnings("unchecked")
    public Health health() {
        try {
            var details=new HashMap<>(queue.status());
            details.put("enabledSources",Map.of("AMAZON",p.amazon().enabled(),"MERCADO_LIVRE",p.mercadoLivre().enabled()));
            details.put("paused",p.telegram().paused());
            var states=(List<Map<String,Object>>)details.get("sources");
            boolean stale=false;
            for (var entry:Map.of("AMAZON",p.amazon(),"MERCADO_LIVRE",p.mercadoLivre()).entrySet()) {
                if (!entry.getValue().enabled()) continue;
                var state=states.stream().filter(s->entry.getKey().equals(s.get("source"))).findFirst();
                if (state.isEmpty() || state.get().get("last_success")==null || state.get().get("last_error")!=null) stale=true;
                else {
                    var last=((java.sql.Timestamp)state.get().get("last_success")).toInstant();
                    if (last.plus(entry.getValue().interval().multipliedBy(3)).isBefore(clock.instant())) stale=true;
                }
            }
            return (stale?Health.status(Status.OUT_OF_SERVICE):Health.up()).withDetails(details).build();
        } catch (RuntimeException e) { return Health.down().withDetail("reason","DATABASE_UNAVAILABLE").build(); }
    }
}
