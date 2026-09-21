# Use a single-language standard scaffold

The standard scaffold owns Simplified Chinese display text directly in components and business modules. Menu titles are persisted display text; dynamic message resources, language negotiation and runtime translation frameworks are removed to keep the scaffold directly maintainable. Third-party components retain only their fixed Chinese configuration.

This variant initializes a fresh database with the independent baseline-2 contract and does not upgrade an internationalized baseline-1 installation. Authentication, authorization, response codes and UTC storage contracts remain unchanged.
