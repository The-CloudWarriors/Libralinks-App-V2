# Library Finder - Final (Validation + Tailwind frontend)

This Spring Boot app uses Google Geocoding + Places APIs and includes input validation.

- ZIP validation: accepts 5–9 digits and rejects values starting with 0 (e.g., 00000 is invalid).
- City validation: uses Geocoding to ensure city exists; otherwise returns "Invalid city name".
- ZIP behavior: returns exact postal_code matches, else nearest library fallback.
- No database used.

## Setup
1. Get a Google API key with Geocoding API and Places API enabled.
2. Replace `google.api.key` in `src/main/resources/application.properties`.
3. Run: `mvn spring-boot:run`
4. Open http://localhost:8080

## Notes
- API calls to Google may incur charges. Consider adding server-side caching for production use.
