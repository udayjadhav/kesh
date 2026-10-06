package com.kesh.integrations.confluence;

import java.util.List;

public interface ConfluenceClient {

    List<ConfluencePage> search(String query);
}
