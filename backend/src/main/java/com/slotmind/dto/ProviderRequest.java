package com.slotmind.dto;

import com.slotmind.domain.enums.ProviderSpecialty;

public record ProviderRequest(String name, ProviderSpecialty specialty) {}