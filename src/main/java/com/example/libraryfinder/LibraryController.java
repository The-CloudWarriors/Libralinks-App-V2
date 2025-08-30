package com.example.libraryfinder;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/libraries")
@CrossOrigin
public class LibraryController {
    private final GooglePlacesService service;

    public LibraryController(GooglePlacesService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam("q") String q) {
        try {
            SearchResponse resp = service.search(q);
            if (!resp.isOk()) {
                return ResponseEntity.badRequest().body(resp);
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(SearchResponse.error("Internal error: " + e.getMessage()));
        }
    }
}
