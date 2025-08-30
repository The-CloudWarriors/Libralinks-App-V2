package com.example.libraryfinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class GooglePlacesService {
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private static final Pattern ZIP_VALID = Pattern.compile("^[1-9][0-9]{4,8}$");

    public GooglePlacesService(@Value("${google.api.key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public SearchResponse search(String q) throws Exception {
        if (q == null || q.isBlank()) return SearchResponse.error("Empty query");

        String trimmed = q.trim();
        if (isZip(trimmed)) {
            return searchByZip(trimmed);
        } else {
            return searchByCity(trimmed);
        }
    }

    private boolean isZip(String s) {
        return ZIP_VALID.matcher(s).matches();
    }

    private SearchResponse searchByZip(String zip) throws Exception {
        String geoUrl = "https://maps.googleapis.com/maps/api/geocode/json?address="
                + URLEncoder.encode(zip, StandardCharsets.UTF_8) + "&key=" + apiKey;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(geoUrl)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode geo = mapper.readTree(resp.body());
        if (!"OK".equals(geo.path("status").asText()) || geo.path("results").size() == 0) {
            return SearchResponse.error("Invalid pincode enter correct pincode");
        }
        JsonNode loc = geo.path("results").get(0).path("geometry").path("location");
        double lat = loc.path("lat").asDouble();
        double lng = loc.path("lng").asDouble();

        String nearby = String.format("https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=%f,%f&radius=12000&type=library&key=%s", lat, lng, apiKey);
        req = HttpRequest.newBuilder().uri(URI.create(nearby)).GET().build();
        resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode nearbyJson = mapper.readTree(resp.body());
        if (nearbyJson.path("results").size() == 0) {
            return SearchResponse.error("No libraries found near this ZIP");
        }

        List<LibraryResult> candidates = new ArrayList<>();
        for (JsonNode place : nearbyJson.path("results")) {
            String placeId = place.path("place_id").asText();
            String details = "https://maps.googleapis.com/maps/api/place/details/json?place_id=" + URLEncoder.encode(placeId, StandardCharsets.UTF_8) + "&fields=name,formatted_address,geometry,address_component&key=" + apiKey;
            HttpRequest dreq = HttpRequest.newBuilder().uri(URI.create(details)).GET().build();
            HttpResponse<String> dresp = http.send(dreq, HttpResponse.BodyHandlers.ofString());
            JsonNode detailsJson = mapper.readTree(dresp.body());
            if (!"OK".equals(detailsJson.path("status").asText())) continue;
            JsonNode result = detailsJson.path("result");
            String formatted = result.path("formatted_address").asText();
            double plat = result.path("geometry").path("location").path("lat").asDouble();
            double plng = result.path("geometry").path("location").path("lng").asDouble();
            String name = result.path("name").asText();

            String foundPostal = null;
            String foundCity = null;
            String foundState = null;
            for (JsonNode comp : result.path("address_components")) {
                for (JsonNode t : comp.path("types")) {
                    String tt = t.asText();
                    if ("postal_code".equals(tt)) {
                        foundPostal = comp.path("long_name").asText();
                    } else if ("locality".equals(tt) || "postal_town".equals(tt)) {
                        foundCity = comp.path("long_name").asText();
                    } else if ("administrative_area_level_1".equals(tt)) {
                        foundState = comp.path("short_name").asText();
                    }
                }
            }
            LibraryResult lr = new LibraryResult(name, formatted, foundCity, foundState, foundPostal, plat, plng);
            candidates.add(lr);
        }

        List<LibraryResult> exact = candidates.stream().filter(c -> zip.equals(c.getPostalCode())).toList();
        if (!exact.isEmpty()) return SearchResponse.ok(exact);

        LibraryResult nearest = candidates.stream()
                .min(Comparator.comparingDouble(c -> haversine(lat, lng, c.getLat(), c.getLng())))
                .orElse(null);
        if (nearest == null) return SearchResponse.error("No libraries found near this ZIP");
        return SearchResponse.ok(List.of(nearest));
    }

    private SearchResponse searchByCity(String city) throws Exception {
        String geoUrl = "https://maps.googleapis.com/maps/api/geocode/json?address="
                + URLEncoder.encode(city, StandardCharsets.UTF_8) + "&key=" + apiKey;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(geoUrl)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode geo = mapper.readTree(resp.body());
        if (!"OK".equals(geo.path("status").asText()) || geo.path("results").size() == 0) {
            return SearchResponse.error("Invalid city name");
        }

        String query = URLEncoder.encode("libraries in " + city, StandardCharsets.UTF_8);
        String textUrl = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=" + query + "&type=library&key=" + apiKey;
        req = HttpRequest.newBuilder().uri(URI.create(textUrl)).GET().build();
        resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode textJson = mapper.readTree(resp.body());
        if (textJson.path("results").size() == 0) return SearchResponse.error("No libraries found in this city");

        List<LibraryResult> out = new ArrayList<>();
        for (JsonNode place : textJson.path("results")) {
            String placeId = place.path("place_id").asText();
            String details = "https://maps.googleapis.com/maps/api/place/details/json?place_id=" + URLEncoder.encode(placeId, StandardCharsets.UTF_8) + "&fields=name,formatted_address,geometry,address_component&key=" + apiKey;
            HttpRequest dreq = HttpRequest.newBuilder().uri(URI.create(details)).GET().build();
            HttpResponse<String> dresp = http.send(dreq, HttpResponse.BodyHandlers.ofString());
            JsonNode detailsJson = mapper.readTree(dresp.body());
            if (!"OK".equals(detailsJson.path("status").asText())) continue;
            JsonNode result = detailsJson.path("result");
            String formatted = result.path("formatted_address").asText();
            double plat = result.path("geometry").path("location").path("lat").asDouble();
            double plng = result.path("geometry").path("location").path("lng").asDouble();
            String name = result.path("name").asText();

            String foundPostal = null;
            String foundCity = null;
            String foundState = null;
            for (JsonNode comp : result.path("address_components")) {
                for (JsonNode t : comp.path("types")) {
                    String tt = t.asText();
                    if ("postal_code".equals(tt)) {
                        foundPostal = comp.path("long_name").asText();
                    } else if ("locality".equals(tt) || "postal_town".equals(tt)) {
                        foundCity = comp.path("long_name").asText();
                    } else if ("administrative_area_level_1".equals(tt)) {
                        foundState = comp.path("short_name").asText();
                    }
                }
            }
            LibraryResult lr = new LibraryResult(name, formatted, foundCity, foundState, foundPostal, plat, plng);
            out.add(lr);
        }
        return SearchResponse.ok(out);
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }
}
