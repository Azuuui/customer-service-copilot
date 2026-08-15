package com.example.copilot.query;

import java.util.List;

public record QueryResponse(String query, int limit, int offset, List<Object> results) {}
