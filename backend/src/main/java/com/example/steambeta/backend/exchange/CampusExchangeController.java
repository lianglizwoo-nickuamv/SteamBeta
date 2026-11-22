package com.example.steambeta.backend.exchange;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exchange/items")
public class CampusExchangeController {
    private final CampusExchangeService exchangeService;

    public CampusExchangeController(CampusExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @GetMapping
    public List<CampusExchangeService.ExchangeItem> list(@RequestParam(required = false) String category) {
        return exchangeService.list(category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampusExchangeService.ExchangeItem create(@RequestBody CampusExchangeService.ExchangeDraft draft) {
        return exchangeService.create(draft);
    }

    @PatchMapping("/{itemId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@PathVariable UUID itemId, @RequestBody CampusExchangeService.StatusUpdate update) {
        exchangeService.updateStatus(itemId, update);
    }
}
