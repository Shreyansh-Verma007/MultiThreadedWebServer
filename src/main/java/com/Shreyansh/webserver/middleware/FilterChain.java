package com.Shreyansh.webserver.middleware;

import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;

public class FilterChain {
    private final List<Filter> filters = new ArrayList<>();

    public void addFilter(Filter filter) {
        filters.add(filter);
    }

    public boolean execute(HttpRequest request, HttpResponse response) {
        for (Filter filter : filters) {
            if (!filter.filter(request, response)) {
                return false;
            }
        }
        return true;
    }
}
