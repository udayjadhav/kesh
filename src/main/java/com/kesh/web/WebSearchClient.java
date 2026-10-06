package com.kesh.web;

import java.util.List;

public interface WebSearchClient {

    List<WebSearchResult> search(String query, int maxResults);
}
