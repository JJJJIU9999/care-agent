package com.jiu.careagent.servicecatalog;

import com.jiu.careagent.security.InternalTokenService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class ServiceCatalogController {
    private final ServiceCatalogService catalog;
    private final InternalTokenService internalToken;

    public ServiceCatalogController(ServiceCatalogService catalog, InternalTokenService internalToken) {
        this.catalog = catalog;
        this.internalToken = internalToken;
    }

    @GetMapping("/api/v1/services")
    public ServiceCatalogService.ServicesResponse publicServices(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return catalog.search(district, category, date);
    }

    @GetMapping("/internal/v1/tools/services")
    public ServiceCatalogService.ServicesResponse internalServices(
            @RequestHeader("X-Internal-Token") String token,
            @RequestHeader("X-Request-ID") UUID requestId,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        internalToken.requireValid(token);
        return catalog.search(district, category, date);
    }
}
