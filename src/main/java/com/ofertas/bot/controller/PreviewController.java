package com.ofertas.bot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofertas.bot.model.OfertaDTO;
import com.ofertas.bot.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Read-only preview: unavailable in live mode; cannot feed synthetic offers into the pipeline. */
@RestController
@ConditionalOnProperty(name="bot.dry-run",havingValue="true",matchIfMissing=true)
public class PreviewController {
    private final JdbcTemplate db; private final ObjectMapper json; private final MessageFormatter formatter;
    public PreviewController(JdbcTemplate db,ObjectMapper json,MessageFormatter formatter) { this.db=db;this.json=json;this.formatter=formatter; }
    @GetMapping("/api/preview")
    public List<Map<String,Object>> previews() {
        return db.query("SELECT id,payload,reason,reference_price,priority,status FROM offer_queue WHERE dry_run=true AND payload IS NOT NULL AND expires_at>now() ORDER BY created_at DESC LIMIT 20",
                (r,n)->{
                    try {
                        var o=json.readValue(r.getString("payload"),OfertaDTO.class);
                        var d=new OfferEvaluator.Decision(true,r.getString("reason"),r.getBigDecimal("reference_price"),r.getBigDecimal("priority"));
                        return Map.<String,Object>of("job",r.getString("id"),"status",r.getString("status"),"html",formatter.format(o,d),"url",o.urlOriginal());
                    } catch (Exception e) { return Map.<String,Object>of("error","PREVIA_INDISPONIVEL"); }
                });
    }
}
