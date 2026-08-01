package com.nestorria.server.modules.contract;

public enum ContractType {
    RENTAL("Contrato de Arrendamiento"),
    PURCHASE("Contrato de Compraventa");

    private final String displayName;

    ContractType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
