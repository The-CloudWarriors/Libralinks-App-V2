package com.example.libraryfinder;

import java.util.List;

public class SearchResponse {
    private boolean ok;
    private String error;
    private List<LibraryResult> results;

    public SearchResponse() {}

    public static SearchResponse ok(List<LibraryResult> results) {
        SearchResponse r = new SearchResponse();
        r.ok = true;
        r.results = results;
        return r;
    }

    public static SearchResponse error(String msg) {
        SearchResponse r = new SearchResponse();
        r.ok = false;
        r.error = msg;
        return r;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public List<LibraryResult> getResults() { return results; }
    public void setResults(List<LibraryResult> results) { this.results = results; }
}
